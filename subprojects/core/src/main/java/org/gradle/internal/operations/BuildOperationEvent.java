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

import com.google.common.base.Preconditions;
import org.gradle.internal.Cast;

/**
 * Base class for build operation events.
 *
 * @param <T> Detail type
 */
abstract class BuildOperationEvent<T> {

    protected final BuildOperationDescriptor descriptor;
    protected final long eventTime;
    protected final T detail;

    BuildOperationEvent(BuildOperationDescriptor descriptor, long eventTime, T detail) {
        Preconditions.checkNotNull(descriptor, "Build Operation Descriptor must not be null");
        this.descriptor = descriptor;
        this.eventTime = eventTime;
        this.detail = detail;
    }

    public BuildOperationDescriptor getDescriptor() {
        return descriptor;
    }

    public long getEventTime() {
        return eventTime;
    }

    public boolean isHasDetail() {
        return detail != null;
    }

    public Class<? extends T> getDetailType() {
        if (isHasDetail()) {
            return Cast.uncheckedCast(detail.getClass());
        }
        throw new IllegalStateException("BuildOperationEvent has no detail, use isHasDetail()");
    }

    public T getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return descriptor.getDisplayName();
    }
}
