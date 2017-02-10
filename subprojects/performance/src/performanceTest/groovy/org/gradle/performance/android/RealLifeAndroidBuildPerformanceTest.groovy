/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.android

import spock.lang.Unroll

class RealLifeAndroidBuildPerformanceTest extends AbstractAndroidPerformanceTest {

    @Unroll("Builds '#testProject' calling #tasks")
    def "build"() {
        given:
        runner.testId = "Android $testProject ${tasks.join(' ').replace(':', '_')}" + (parallel ? " (parallel)" : "")
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.gradleOpts = ["-Xms$memory", "-Xmx$memory"]
        runner.args = parallel ? ['-Dorg.gradle.parallel=true', '-Dorg.gradle.parallel.intra=true'] : []
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.targetVersions = ["3.4-20170124101339+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject         | memory | parallel | warmUpRuns | runs | tasks
        'k9AndroidBuild'    | '512m' | false    | null       | null | ['help']
        'k9AndroidBuild'    | '512m' | false    | null       | null | ['clean', 'k9mail:assembleDebug']
        'largeAndroidBuild' | '2g'   | false    | null       | null | ['help']
        'largeAndroidBuild' | '2g'   | true     | 2          | 6    | ['clean', 'phthalic:assembleDebug']
    }
}
