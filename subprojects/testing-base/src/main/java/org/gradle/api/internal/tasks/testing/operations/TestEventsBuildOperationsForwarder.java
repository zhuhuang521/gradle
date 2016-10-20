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

package org.gradle.api.internal.tasks.testing.operations;

import org.gradle.api.Nullable;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.operations.details.TestOperationDetail;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationFinished;
import org.gradle.internal.operations.BuildOperationStarted;
import org.gradle.internal.operations.BuildOperationTypes;
import org.gradle.internal.operations.BuildOperationsListener;

/**
 * Forward test execution events to {@link BuildOperationsListener}.
 */
public class TestEventsBuildOperationsForwarder implements TestListenerInternal {

    private final BuildOperationsListener<Object> broadcaster;

    public TestEventsBuildOperationsForwarder(BuildOperationsListener<Object> broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        broadcaster.operationStarted(new BuildOperationStarted<Object>(toDescriptor(testDescriptor), startEvent.getStartTime(), getDetail(testDescriptor, null)));
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        broadcaster.operationFinished(new BuildOperationFinished<Object>(toDescriptor(testDescriptor), testResult.getStartTime(), completeEvent.getEndTime(), testResult.getException(), getDetail(testDescriptor, testResult)));
    }

    private BuildOperationDescriptor toDescriptor(TestDescriptorInternal testDescriptor) {
        return BuildOperationDescriptor.builder()
            .id(testDescriptor.getId())
            .parentId(getParent(testDescriptor))
            .displayName(testDescriptor.toString())
            .type(BuildOperationTypes.Tests.class)
            .build();
    }

    private Object getParent(TestDescriptorInternal testDescriptor) {
        return testDescriptor.getParent() == null ? testDescriptor.getOwnerBuildOperationId() : testDescriptor.getParent().getId();
    }

    private TestOperationDetail getDetail(TestDescriptorInternal testDescriptor, @Nullable TestResult testResult) {
        String name = testDescriptor.getName();
        String displayName = testDescriptor.toString();
        boolean composite = testDescriptor.isComposite();
        String suiteName = testDescriptor.isComposite() ? name : null;
        String className = testDescriptor.getClassName();
        String methodName = testDescriptor.isComposite() ? null : name;
        TestResult.ResultType resultType = testResult != null ? testResult.getResultType() : null;
        Long testCount = testResult != null ? testResult.getTestCount() : null;
        Long successfulTestCount = testResult != null ? testResult.getSuccessfulTestCount() : null;
        Long failedTestCount = testResult != null ? testResult.getFailedTestCount() : null;
        Long skippedTestCount = testResult != null ? testResult.getSkippedTestCount() : null;
        return new TestOperationDetail(name, displayName, composite, suiteName, className, methodName, resultType, testCount, successfulTestCount, failedTestCount, skippedTestCount);
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        // Not forwarded as build operations
    }
}
