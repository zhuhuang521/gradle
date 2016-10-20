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

package org.gradle.internal.operations.details;

import org.gradle.api.internal.tasks.TaskExecutionOutcome;

public class TaskOperationDetail {
    private final String name;
    private final String group;
    private final String description;
    private final String path;
    private final boolean cacheable;
    private final boolean didWork;
    private final TaskExecutionOutcome outcome;

    public TaskOperationDetail(String name, String group, String description, String path, boolean cacheable, boolean didWork, TaskExecutionOutcome outcome) {
        this.name = name;
        this.group = group;
        this.description = description;
        this.path = path;
        this.cacheable = cacheable;
        this.didWork = didWork;
        this.outcome = outcome;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public String getPath() {
        return path;
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public boolean isDidWork() {
        return didWork;
    }

    public TaskExecutionOutcome getOutcome() {
        return outcome;
    }

    @Override
    public String toString() {
        return path + (outcome == null ? "" : " " + outcome);
    }
}
