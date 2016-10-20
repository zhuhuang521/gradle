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

package org.gradle.internal.operations;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.internal.Cast;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// TODO Change pipes key type to avoid keeping references to Class objects, questions are, by what?, why?
public class DefaultBuildOperationEventBus implements BuildOperationsEventBus {

    private static class Subscription implements BuildOperationSubscription {
        private final Set<Class<?>> types;
        private final Action<Subscription> unsubscribeAction;

        private Subscription(Action<Subscription> unsubscribeAction) {
            this(unsubscribeAction, ImmutableSet.<Class<?>>of());
        }

        private Subscription(Action<Subscription> unsubscribeAction, Set<Class<?>> types) {
            this.unsubscribeAction = unsubscribeAction;
            this.types = types;
        }

        @Override
        public void unsubscribe() {
            unsubscribeAction.execute(this);
        }
    }

    private static class Pipe {
        private final ForwarderListener forwarder;
        private final ListenerBroadcast<BuildOperationsListener> broadcaster;

        private Pipe(ForwarderListener forwarder, ListenerBroadcast<BuildOperationsListener> broadcaster) {
            this.forwarder = forwarder;
            this.broadcaster = broadcaster;
        }
    }

    private static class ForwarderListener implements BuildOperationsListener<Object> {
        private final Set<Class<?>> types;
        private final BuildOperationsListener sink;
        private final Map<Object, Object> allParents = new HashMap<Object, Object>();
        private final Map<Object, Object> forwardedParents = new HashMap<Object, Object>();

        private ForwarderListener(Set<Class<?>> types, BuildOperationsListener sink) {
            this.types = types;
            this.sink = sink;
        }

        @Override
        public void operationStarted(BuildOperationStarted<Object> event) {
            allParents.put(event.getDescriptor().getId(), event.getDescriptor().getParentId());
            if (!isForwarded(event)) {
                return;
            }
            Object parentId = event.getDescriptor().getParentId();
            if (forwardedParents.isEmpty() || parentId == null) {
                // Root or first forwarded build operation
                parentId = null;
                forwardedParents.put(event.getDescriptor().getId(), null);
            } else {
                // Subsequent forwarded build operation
                if (!forwardedParents.containsKey(event.getDescriptor().getParentId())) {
                    // Direct parent build operation was not forwarded, needs remapping
                    Object current = event.getDescriptor().getParentId();
                    while (current != null) {
                        current = allParents.get(current);
                        if (forwardedParents.containsKey(current)) {
                            parentId = current;
                            break;
                        }
                    }
                }
                forwardedParents.put(event.getDescriptor().getId(), parentId);
            }

            BuildOperationStarted<Object> forwarded = event;
            if (!Objects.equal(parentId, event.getDescriptor().getParentId())) {
                forwarded = event.withParentId(parentId);
            }
            sink.operationStarted(forwarded);
        }

        @Override
        public void operationFinished(BuildOperationFinished<Object> event) {
            if (!isForwarded(event)) {
                allParents.remove(event.getDescriptor().getId());
                return;
            }
            BuildOperationFinished<Object> forwarded = event;
            Object forwardedParentId = forwardedParents.get(event.getDescriptor().getId());
            if (!Objects.equal(event.getDescriptor().getParentId(), forwardedParentId)) {
                forwarded = event.withParentId(forwardedParentId);
            }
            allParents.remove(event.getDescriptor().getId());
            forwardedParents.remove(event.getDescriptor().getId());
            sink.operationFinished(forwarded);
        }

        private boolean isForwarded(BuildOperationEvent<Object> event) {
            for (Class<?> eventType : event.getDescriptor().getTypes()) {
                for (Class<?> forwardedType : types) {
                    if (forwardedType.isAssignableFrom(eventType)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private final ListenerManager listenerManager;
    private final ListenerManager pipesManager;

    // The following state is protected by lock
    private final Map<Set<Class<?>>, Pipe> pipes = new HashMap<Set<Class<?>>, Pipe>();

    public DefaultBuildOperationEventBus(final ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
        this.pipesManager = new DefaultListenerManager();
        registerInternalEventsForwarders();
    }

    private void registerInternalEventsForwarders() {
        BuildOperationsListener<Object> broadcaster = Cast.uncheckedCast(listenerManager.getBroadcaster(BuildOperationsListener.class));
        DefaultInternalEventsBuildOperationsForwarder forwarder = new DefaultInternalEventsBuildOperationsForwarder(broadcaster);
        listenerManager.addListener(forwarder);
    }

    @Override
    public BuildOperationSubscription subscribe(final BuildOperationsListener<?> listener, Class<?>... buildOperationTypes) {
        Set<Class<?>> types = Sets.filter(Sets.newHashSet(buildOperationTypes), Predicates.notNull());
        if (types.isEmpty()) {
            return subscribeDirectly(listener);
        }
        return subscribeThroughPipe(listener, types);
    }

    private BuildOperationSubscription subscribeDirectly(final BuildOperationsListener<?> listener) {
        // No pipe needed, delegate to listener manager directly
        listenerManager.addListener(listener);
        return new Subscription(new Action<Subscription>() {
            @Override
            public void execute(Subscription subscription) {
                listenerManager.removeListener(listener);
            }
        });
    }

    private BuildOperationSubscription subscribeThroughPipe(final BuildOperationsListener<?> listener, Set<Class<?>> types) {
        // Pipe through anonymous broadcasters per filter
        synchronized (pipes) {
            Set<Class<?>> reducedTypes = reduceTypes(types);
            final Pipe pipe = pipeFor(reducedTypes);
            pipe.broadcaster.add(listener);
            Action<Subscription> unsubscribeAction = new Action<Subscription>() {
                @Override
                public void execute(Subscription subscription) {
                    synchronized (pipes) {
                        pipe.broadcaster.remove(listener);
                        eventuallyEvictPipe(subscription.types);
                    }
                }
            };
            return new Subscription(unsubscribeAction, reducedTypes);
        }
    }

    // Must be holding lock
    private Pipe pipeFor(Set<Class<?>> types) {
        Pipe pipe = pipes.get(types);
        if (pipe == null) {
            ListenerBroadcast<BuildOperationsListener> anonymousBroadcaster = pipesManager.createAnonymousBroadcaster(BuildOperationsListener.class);
            ForwarderListener forwarderListener = new ForwarderListener(types, anonymousBroadcaster.getSource());
            listenerManager.addListener(forwarderListener);
            pipe = new Pipe(forwarderListener, anonymousBroadcaster);
            pipes.put(types, pipe);
        }
        return pipe;
    }

    // Must be holding lock
    private void eventuallyEvictPipe(Set<Class<?>> types) {
        Pipe pipe = pipes.get(types);
        if (pipe != null && pipe.broadcaster.isEmpty()) {
            listenerManager.removeListener(pipe.forwarder);
            pipes.remove(types);
        }
    }

    @VisibleForTesting
    static Set<Class<?>> reduceTypes(Set<Class<?>> types) {
        if (types == null || types.isEmpty()) {
            return ImmutableSet.of();
        }
        if (types.size() == 1) {
            return ImmutableSet.<Class<?>>of(types.iterator().next());
        }
        // remove redundant types
        Iterator<Class<?>> typesIterator = types.iterator();
        Set<Class<?>> flattened = Sets.newHashSet();
        flattened.add(typesIterator.next());
        while (typesIterator.hasNext()) {
            Class<?> next = typesIterator.next();
            Iterator<Class<?>> flattenedIterator = flattened.iterator();
            boolean doAdd = true;
            while (flattenedIterator.hasNext()) {
                Class<?> current = flattenedIterator.next();
                if (current.isAssignableFrom(next)) {
                    doAdd = false;
                } else if (next.isAssignableFrom(current)) {
                    flattenedIterator.remove();
                }
            }
            if (doAdd) {
                flattened.add(next);
            }
        }
        // predictable order
        return ImmutableSortedSet.copyOf(new Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> left, Class<?> right) {
                return left.getName().compareTo(right.getName());
            }
        }, flattened);
    }
}
