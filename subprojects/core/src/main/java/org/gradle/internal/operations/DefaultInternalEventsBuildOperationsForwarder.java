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

import org.gradle.api.execution.internal.InternalTaskExecutionListener;
import org.gradle.api.execution.internal.TaskOperationInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.internal.operations.details.TaskOperationDetail;
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;

/**
 * Message broker between internal events and {@link BuildOperationsListener}.
 *
 * Internal listeners kept for backward compatibility until we can remove them in favor of proper build operations.
 */
class DefaultInternalEventsBuildOperationsForwarder implements InternalBuildListener, InternalTaskExecutionListener {

    private final BuildOperationsListener<Object> broadcaster;

    DefaultInternalEventsBuildOperationsForwarder(BuildOperationsListener<Object> broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void started(BuildOperationInternal operation, OperationStartEvent event) {
        broadcaster.operationStarted(new BuildOperationStarted<Object>(toDescriptor(operation), event.getStartTime(), null));
    }

    @Override
    public void finished(BuildOperationInternal operation, OperationResult event) {
        broadcaster.operationFinished(new BuildOperationFinished<Object>(toDescriptor(operation), event.getStartTime(), event.getEndTime(), event.getFailure(), null));
    }

    private BuildOperationDescriptor toDescriptor(BuildOperationInternal operation) {
        BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.builder()
            .id(operation.getId())
            .parentId(operation.getParentId())
            .displayName(operation.getDisplayName());
        // TODO This is fragile, need to impact BuildOperationExecutor to distinguish operation types
        if ("Run tasks".equals(operation.getDisplayName())) {
            builder.types(BuildOperationTypes.Tasks.class);
        } else {
            builder.types(BuildOperationTypes.Configuration.class);
        }
        return builder.build();
    }

    @Override
    public void beforeExecute(TaskOperationInternal operation, OperationStartEvent event) {
        broadcaster.operationStarted(new BuildOperationStarted<Object>(toDescriptor(operation), event.getStartTime(), buildDetail(operation)));
    }

    @Override
    public void afterExecute(TaskOperationInternal operation, OperationResult event) {
        broadcaster.operationFinished(new BuildOperationFinished<Object>(toDescriptor(operation), event.getStartTime(), event.getEndTime(), event.getFailure(), buildDetail(operation)));
    }

    private BuildOperationDescriptor toDescriptor(TaskOperationInternal operation) {
        return BuildOperationDescriptor.builder()
            .id(operation.getId())
            .parentId(operation.getParentId())
            .displayName(toDisplayName(operation))
            .type(BuildOperationTypes.Tasks.class)
            .build();
    }

    private String toDisplayName(TaskOperationInternal operation) {
        return "Task " + operation.getTask().getPath();
    }

    private TaskOperationDetail buildDetail(TaskOperationInternal operation) {
        TaskInternal task = operation.getTask();
        String name = task.getName();
        String group = task.getGroup();
        String description = task.getDescription();
        String path = task.getPath();
        boolean cacheable = task.getState().isCacheable();
        boolean didWork = task.getState().getDidWork();
        TaskExecutionOutcome outcome = task.getState().getOutcome();
        return new TaskOperationDetail(name, group, description, path, cacheable, didWork, outcome);
    }
}
