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

/**
 * Event Bus for Build Operation events.
 *
 * Allow listeners to declare which type of operation they are interested in.
 * Hierarchical layout of events reflects subscribed build operation types.
 */
public interface BuildOperationsEventBus {

    /**
     * Subscribe to build operation events.
     *
     * @param listener Will be notified of build operation events
     * @param buildOperationTypes Build operation types the listener is interested in
     * @return Subscription that allows to unsubscribe
     */
    BuildOperationSubscription subscribe(BuildOperationsListener<?> listener, Class<?>... buildOperationTypes);

}
