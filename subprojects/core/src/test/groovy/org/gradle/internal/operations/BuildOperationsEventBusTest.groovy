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

import spock.lang.Specification
import spock.lang.Unroll
import org.gradle.internal.operations.BuildOperationTypes.Configuration
import org.gradle.internal.operations.BuildOperationTypes.Execution
import org.gradle.internal.operations.BuildOperationTypes.Network
import org.gradle.internal.operations.BuildOperationTypes.Tasks
import org.gradle.internal.operations.BuildOperationTypes.Tests

class BuildOperationsEventBusTest extends Specification {
    @Unroll
    def "flatten build operations types for subscriptions - #usecase"() {
        when:
        def flattened = DefaultBuildOperationEventBus.reduceTypes(types as Set)

        then:
        flattened == expected as Set

        and:
        Arrays.equals(flattened as Class<?>[], expected as Class<?>[])

        where:
        usecase            | types                             | expected
        'null'             | null                              | []
        'empty'            | []                                | []
        'single'           | [Network]                         | [Network]
        'duplicates'       | [Network, Network]                | [Network]
        'type-hierarchy-1' | [Execution, Tasks]                | [Execution]
        'type-hierarchy-2' | [Tests, Execution]                | [Execution]
        'type-hierarchy-3' | [Tests, Execution, Tasks]         | [Execution]
        'type-hierarchy-4' | [Execution, Tasks, Configuration] | [Configuration, Execution]
    }
}
