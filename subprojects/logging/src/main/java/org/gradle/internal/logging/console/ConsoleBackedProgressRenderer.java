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
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
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
    private final ConsoleMetaData consoleMetaData;
    private final BuildStatusRenderer buildStatusRenderer;
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
    private OperationIdentifier rootOperationId;
    // TODO: Replace with operation status area
    private Label statusBar;
    private ProgressRenderer progressRenderer;

    public ConsoleBackedProgressRenderer(OutputEventListener listener, Console console, ConsoleMetaData consoleMetaData, DefaultStatusBarFormatter statusBarFormatter, TimeProvider timeProvider) {
        this(listener, console, consoleMetaData, statusBarFormatter, Integer.getInteger("org.gradle.console.throttle", 85), Executors.newSingleThreadScheduledExecutor(), timeProvider);
    }

    ConsoleBackedProgressRenderer(OutputEventListener listener, Console console, ConsoleMetaData consoleMetaData, DefaultStatusBarFormatter statusBarFormatter, int throttleMs, ScheduledExecutorService executor, TimeProvider timeProvider) {
        this.throttleMs = throttleMs;
        this.listener = listener;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
        this.statusBarFormatter = statusBarFormatter;
        this.executor = executor;
        this.timeProvider = timeProvider;
        this.progressRenderer = new ProgressRenderer(console.getBuildProgressArea().getBuildProgressLabels());
        this.buildStatusRenderer = new BuildStatusRenderer(getStatusBar());
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
        for (OutputEvent event : queue) {
            try {
                if (event instanceof ProgressStartEvent) {
                    ProgressStartEvent startEvent = (ProgressStartEvent) event;
                    // if it has no parent ID, assign this operation as the root operation
                    if (startEvent.getParentId() == null) {
                        rootOperationId = startEvent.getOperationId();
                        buildStatusRenderer.buildStarted(startEvent);
                    }
                    ProgressOperation op = operations.start(startEvent.getShortDescription(), startEvent.getStatus(), startEvent.getOperationId(), startEvent.getParentId());
                    progressRenderer.attach(op);
                } else if (event instanceof ProgressCompleteEvent) {
                    ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
                    if (completeEvent.getOperationId().equals(rootOperationId)) {
                        buildStatusRenderer.buildFinished(completeEvent);
                    }
                    progressRenderer.detach(operations.complete(completeEvent.getOperationId()));
                } else if (event instanceof ProgressEvent) {
                    ProgressEvent progressEvent = (ProgressEvent) event;
                    if (progressEvent.getOperationId().equals(rootOperationId)) {
                        buildStatusRenderer.buildProgressed(progressEvent);
                    }
                    operations.progress(progressEvent.getStatus(), progressEvent.getOperationId());
                }
                listener.onOutput(event);
            } catch (Exception e) {
                throw new RuntimeException("Unable to process incoming event '" + event + "' (" + event.getClass().getSimpleName() + ")", e);
            }
        }

        progressRenderer.renderNow();
        buildStatusRenderer.renderNow();

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

    // Maintain overall build status separately
    private class BuildStatusRenderer {
        private final Label buildStatusLabel;
        private String currentBuildStatus;

        private BuildStatusRenderer(Label buildStatusLabel) {
            this.buildStatusLabel = buildStatusLabel;
        }

        void buildStarted(ProgressStartEvent progressStartEvent) {
            currentBuildStatus = progressStartEvent.getShortDescription();
        }

        void buildProgressed(ProgressEvent progressEvent) {
            currentBuildStatus = progressEvent.getStatus();
        }

        void buildFinished(ProgressCompleteEvent progressCompleteEvent) {
            currentBuildStatus = "";
        }

        String trimToConsole(String str) {
            int width = consoleMetaData.getCols() - 1;
            if (width > 0 && width < str.length()) {
                return str.substring(0, width);
            }
            return str;
        }

        void renderNow() {
            if (currentBuildStatus != null) {
                buildStatusLabel.setText(trimToConsole(currentBuildStatus));
            }
        }
    }
}
