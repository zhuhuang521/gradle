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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Build operation descriptor.
 */
public class BuildOperationDescriptor {

    /**
     * Build operation descriptor builder.
     */
    public static class Builder {
        private Object id;
        private Object parentId;
        private String displayName;
        private ImmutableSet.Builder<Class<?>> typesBuilder;

        private Builder() {
            typesBuilder = ImmutableSet.builder();
        }

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        public Builder parentId(Object parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder type(Class<?> buildOperationType) {
            this.typesBuilder.add(buildOperationType);
            return this;
        }

        public Builder types(Class<?>... buildOperationTypes) {
            this.typesBuilder.add(buildOperationTypes);
            return this;
        }

        public BuildOperationDescriptor build() {
            Preconditions.checkNotNull(id, "Build Operation ID must not be null");
            Preconditions.checkArgument(!Strings.isNullOrEmpty(displayName), "Build Operation display name must not be empty");
            return new BuildOperationDescriptor(id, parentId, displayName, typesBuilder.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Object id;
    private final Object parentId;
    private final String displayName;
    private final Set<Class<?>> types;

    private BuildOperationDescriptor(Object id, Object parentId, String displayName, Set<Class<?>> types) {
        this.id = id;
        this.parentId = parentId;
        this.displayName = displayName;
        this.types = types;
    }

    public Object getId() {
        return id;
    }

    public Object getParentId() {
        return parentId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Class<?>> getTypes() {
        return types;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    BuildOperationDescriptor withParentId(Object parentId) {
        return new BuildOperationDescriptor(id, parentId, displayName, types);
    }
}
