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

package org.gradle.api.internal.tasks.testing.operations.details;

import org.gradle.api.tasks.testing.TestResult;

public class TestOperationDetail {
    private final String name;
    private final String displayName;
    private final boolean composite;
    private final String suiteName;
    private final String className;
    private final String methodName;
    private final TestResult.ResultType resultType;
    private final Long testCount;
    private final Long successfulTestCount;
    private final Long failedTestCount;
    private final Long skippedTestCount;

    public TestOperationDetail(String name, String displayName, boolean composite, String suiteName, String className, String methodName, TestResult.ResultType resultType, Long testCount, Long successfulTestCount, Long failedTestCount, Long skippedTestCount) {
        this.name = name;
        this.displayName = displayName;
        this.composite = composite;
        this.suiteName = suiteName;
        this.className = className;
        this.methodName = methodName;
        this.resultType = resultType;
        this.testCount = testCount;
        this.successfulTestCount = successfulTestCount;
        this.failedTestCount = failedTestCount;
        this.skippedTestCount = skippedTestCount;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isComposite() {
        return composite;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public TestResult.ResultType getResultType() {
        return resultType;
    }

    public Long getTestCount() {
        return testCount;
    }

    public Long getSuccessfulTestCount() {
        return successfulTestCount;
    }

    public Long getFailedTestCount() {
        return failedTestCount;
    }

    public Long getSkippedTestCount() {
        return skippedTestCount;
    }

    @Override
    public String toString() {
        return displayName + (resultType == null ? "" : " " + resultType);
    }
}
