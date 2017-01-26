/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.logging.console;

import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.time.TimeProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsoleBackedProgressRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private final Console console;
    // TODO: use ProgressOperations to maintain all operations in progress
    private final ProgressOperations operations = new ProgressOperations();
    private final DefaultStatusBarFormatter statusBarFormatter;
    private final ScheduledExecutorService executor;
    private final TimeProvider timeProvider;
    private final int throttleMs;
    // Protected by lock
    private final Object lock = new Object();
    private long lastUpdate;
    private final List<OutputEvent> queue = new ArrayList<OutputEvent>();
    // TODO: Replace with Fixed size HashMap with stable ordering
    private ProgressOperation mostRecentOperation;
    // TODO: Replace with operation status area
    private Label statusBar;
    private ProgressRenderer progressRenderer;


    // TODO(EW): Seems like we want to maintain both the header and the worker statuses in here to avoid thrashing from having two entities updating the status area (different scheduled executors)
    public ConsoleBackedProgressRenderer(OutputEventListener listener, Console console, DefaultStatusBarFormatter statusBarFormatter, TimeProvider timeProvider) {
        this(listener, console, statusBarFormatter, Integer.getInteger("org.gradle.console.throttle", 85), Executors.newSingleThreadScheduledExecutor(), timeProvider);
    }

    ConsoleBackedProgressRenderer(OutputEventListener listener, Console console, DefaultStatusBarFormatter statusBarFormatter, int throttleMs, ScheduledExecutorService executor, TimeProvider timeProvider) {
        this.throttleMs = throttleMs;
        this.listener = listener;
        this.console = console;
        this.statusBarFormatter = statusBarFormatter;
        this.executor = executor;
        this.timeProvider = timeProvider;
        this.progressRenderer = new ProgressRenderer(console.getBuildProgressArea().getBuildProgressLabels());
    }

    public void onOutput(OutputEvent newEvent) {
        synchronized (lock) {
            queue.add(newEvent);

            if (newEvent instanceof EndOutputEvent) {
                // Flush and clean up
                renderNow(timeProvider.getCurrentTime());
                executor.shutdown();
                return;
            }

            if (queue.size() > 1) {
                // Currently queuing events, a thread is scheduled to flush the queue later
                return;
            }

            long now = timeProvider.getCurrentTime();
            if (now - lastUpdate >= throttleMs) {
                // Has been long enough since last update - flush now
                renderNow(now);
                return;
            }

            // This is the first queued event - schedule a thread to flush later
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        renderNow(timeProvider.getCurrentTime());
                    }
                }
            }, throttleMs, TimeUnit.MILLISECONDS);
        }
    }

    private void renderNow(long now) {
        if (queue.isEmpty()) {
            // Already rendered - don't update anything
            return;
        }

        // TODO: Render Up to 4 events in progress
        // Best if we can avoid re-writing operations that haven't changed
        ProgressOperation lastOp = mostRecentOperation;
        for (OutputEvent event : queue) {
            try {
                if (event instanceof ProgressStartEvent) {
                    ProgressStartEvent startEvent = (ProgressStartEvent) event;
                    lastOp = operations.start(startEvent.getShortDescription(), startEvent.getStatus(), startEvent.getOperationId(), startEvent.getParentId());
                    progressRenderer.attach(lastOp);
                } else if (event instanceof ProgressCompleteEvent) {
                    ProgressOperation op = operations.complete(((ProgressCompleteEvent) event).getOperationId());
                    lastOp = op.getParent();
                    progressRenderer.detach(op);
                } else if (event instanceof ProgressEvent) {
                    ProgressEvent progressEvent = (ProgressEvent) event;
                    lastOp = operations.progress(progressEvent.getStatus(), progressEvent.getOperationId());
                }
                listener.onOutput(event);
            } catch (Exception e) {
                throw new RuntimeException("Unable to process incoming event '" + event + "' (" + event.getClass().getSimpleName() + ")", e);
            }
        }

        progressRenderer.renderNow();

        if (lastOp != null) {
            getStatusBar().setText(statusBarFormatter.format(lastOp));
        } else if (mostRecentOperation != null) {
            getStatusBar().setText("");
        }
        mostRecentOperation = lastOp;
        queue.clear();
        lastUpdate = now;
        console.flush();
    }

    private Label getStatusBar() {
        if (statusBar == null) {
            statusBar = console.getStatusBar();
        }
        return statusBar;
    }

    private class ProgressRenderer {
        private final Deque<Label> unusedProgressLabels;
        private final Map<OperationIdentifier, AssociationLabel> operationIdToAssignedLabels = new HashMap<OperationIdentifier, AssociationLabel>();
        private final Set<OperationIdentifier> receivedParentOperationId = new HashSet<OperationIdentifier>();
        private final Deque<ProgressOperation> unassignedProgressOperations = new ArrayDeque<ProgressOperation>();

        ProgressRenderer(Collection<Label> progressLabels) {
            this.unusedProgressLabels = new ArrayDeque<Label>(progressLabels);
        }

        public void attach(ProgressOperation operation) {
            AssociationLabel association = null;
            if (operation.getParent() != null) {
                association = operationIdToAssignedLabels.remove(operation.getParent().getOperationId());
                if (association != null) {
                    receivedParentOperationId.add(operation.getParent().getOperationId());
                }
            }
            if (association == null && !unusedProgressLabels.isEmpty()) {
                association = new AssociationLabel(operation, unusedProgressLabels.pop());
            }

            if (association == null) {
                unassignedProgressOperations.addLast(operation);
            } else {
                operationIdToAssignedLabels.put(operation.getOperationId(), association);
            }
        }

        public void detach(ProgressOperation operation) {
            AssociationLabel association = operationIdToAssignedLabels.remove(operation.getOperationId());
            if (association != null) {
                association.label.setText("");
                unusedProgressLabels.push(association.label);
                if (operation.getParent() != null && receivedParentOperationId.remove(operation.getParent().getOperationId())) {
                    attach(operation.getParent());
                } else if (!unassignedProgressOperations.isEmpty()){
                    attach(unassignedProgressOperations.pop());
                }
            } else {
                unassignedProgressOperations.remove(operation);
            }
        }

        public void renderNow() {
            for (AssociationLabel associatedLabel : operationIdToAssignedLabels.values()) {
                associatedLabel.renderNow();
            }
        }

        private class AssociationLabel {
            final ProgressOperation operation;
            final Label label;

            AssociationLabel(ProgressOperation operation, Label label) {
                this.operation = operation;
                this.label = label;
            }

            public void renderNow() {
                label.setText(statusBarFormatter.format(operation));
            }
        }
    }
}
