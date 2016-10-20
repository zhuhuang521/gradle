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

package org.gradle.internal.operations

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildOperationsEventBusIntegrationTest extends AbstractIntegrationSpec {

    def initFile = file('init-listener.gradle')

    def setup() {
        initFile << '''
            import org.gradle.internal.operations.*
            import groovy.json.*
            def listener = new BuildOperationsEventBusIntegrationTest.CaptureBuildOperationsListener()
            gradle.buildFinished {
                println JsonOutput.prettyPrint(JsonOutput.toJson(listener.all))
            }
        '''.stripIndent()
        basicJavaProject()
        args '-q', '-I', initFile.absolutePath
    }

    def "forward all events when no filtering"() {
        given:
        listenTo()

        when:
        run 'build'

        then:
        def events = new JsonSlurper().parseText(output)
        events.size() == 19
        events.findAll { it.displayName == 'Configure build' }.size() == 1
        events.findAll { it.displayName == 'Task :compileJava' }.size() == 1
        events.findAll { it.displayName == 'Test testMethod(foo.FooTest)' }.size() == 1
    }

    def "test execution events have detail"() {
        given:
        listenTo('Tests')

        when:
        run 'build'

        then:
        def events = new JsonSlurper().parseText(output)
        events.size() == 4
        def testMethod = events.find { it.displayName == 'Test testMethod(foo.FooTest)' }
        testMethod.finished.detail.endsWith 'SUCCESS'
    }

    def "cohesive events subset has a root with no parent"() {
        given:
        listenTo('Tasks')

        when:
        run 'build'

        then:
        def events = new JsonSlurper().parseText(output)
        events.size() == 12
        def runTasks = events.find { it.displayName == 'Run tasks' }
        runTasks.parentId == null
        events.findAll { it.displayName.startsWith('Task :') }.size() == 11
        events.findAll { it.displayName.startsWith('Task :') && it.parentId != null }.size() == 11
    }

    def "disjoint events subset hierarchy is coherent"() {
        // TODO We don't have any disjoint events subsets yet, this test is here to ensure it will work when we introduce more events
        given:
        listenTo('Tasks', 'Network')

        when:
        run 'build'

        then:
        def events = new JsonSlurper().parseText(output)
        events.each { event ->
            if (event.parentId != null) {
                assert events.findAll { it.id == event.parentId }.size() == 1
            }
        }
    }

    private void listenTo(String... eventTypes) {
        if (!eventTypes) {
            initFile << """
                gradle.services.get(BuildOperationsEventBus).subscribe(listener)
            """.stripIndent()
        } else {
            initFile << """
                gradle.services.get(BuildOperationsEventBus).subscribe(listener, ${eventTypes.collect { "BuildOperationTypes.$it " }.join(', ')})
            """.stripIndent()
        }
    }

    private void basicJavaProject() {
        buildFile << '''
            apply plugin: 'java'
            repositories {
                jcenter()
            }
            dependencies {
                compile 'org.slf4j:slf4j-api:1.7.21'
                testCompile 'junit:junit:4.12'
            }
        '''.stripIndent()
        file('src/main/java/foo').mkdirs()
        file('src/test/java/foo').mkdirs()
        file('src/main/java/foo/Foo.java') << '''
            package foo;
            public class Foo {
                public static void main(String[] args) {}
            }
        '''.stripIndent()
        file('src/test/java/foo/FooTest.java') << '''
            package foo;
            import org.junit.Test;
            public class FooTest {
                @Test
                public void testMethod() {}
            }
        '''.stripIndent()
    }

    class CaptureBuildOperationsListener implements BuildOperationsListener<Object> {

        List<Map> all = []

        @Override
        void operationStarted(BuildOperationStarted<Object> event) {
            all << [
                id: event.descriptor.id?.toString(),
                parentId: event.descriptor.parentId?.toString(),
                displayName: event.descriptor.displayName,
                types: event.descriptor.types.collect { it.simpleName },
                started: toMap(event),
                finished: null
            ]
        }

        @Override
        void operationFinished(BuildOperationFinished<Object> event) {
            all.each {
                if (it['id'] == event.descriptor.id?.toString()) {
                    it['finished'] = toMap(event)
                }
            }
        }

        private static Map toMap(BuildOperationEvent<Object> event) {
            [time: event.eventTime, detail: event.detail.toString()]
        }
    }
}
