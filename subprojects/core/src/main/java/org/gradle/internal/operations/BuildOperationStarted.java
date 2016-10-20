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
 * Event emitted when a build operation is started.
 *
 * @param <T> Detail type
 */
public class BuildOperationStarted<T> extends BuildOperationEvent<T> {
    public BuildOperationStarted(BuildOperationDescriptor descriptor, long eventTime, T detail) {
        super(descriptor, eventTime, detail);
    }

    BuildOperationStarted<T> withParentId(Object parentId) {
        return new BuildOperationStarted<T>(descriptor.withParentId(parentId), eventTime, detail);
    }
}
