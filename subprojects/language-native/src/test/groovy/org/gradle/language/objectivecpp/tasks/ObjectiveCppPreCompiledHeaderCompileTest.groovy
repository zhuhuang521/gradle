/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.objectivecpp.tasks
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppPCHCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class ObjectiveCppPreCompiledHeaderCompileTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()

    ObjectiveCppPreCompiledHeaderCompile objCppPCHCompile = TestUtil.create(testDir).task(ObjectiveCppPreCompiledHeaderCompile)
    def toolChain = Mock(NativeToolChainInternal)
    def platform = Mock(NativePlatformInternal)
    def platformToolChain = Mock(PlatformToolProvider)
    Compiler<ObjectiveCppPCHCompileSpec> objCppPCHCompiler = Mock(Compiler)

    def "executes using the Cpp PCH Compiler"() {
        def sourceFile = testDir.createFile("sourceFile")
        def result = Mock(WorkResult)
        when:
        objCppPCHCompile.toolChain = toolChain
        objCppPCHCompile.targetPlatform = platform
        objCppPCHCompile.compilerArgs = ["arg"]
        objCppPCHCompile.macros = [def: "value"]
        objCppPCHCompile.objectFileDir = testDir.file("outputFile")
        objCppPCHCompile.source sourceFile
        objCppPCHCompile.execute()

        then:
        _ * toolChain.outputType >> "objcpp"
        platform.getName() >> "testPlatform"
        platform.getArchitecture() >> Mock(ArchitectureInternal) { getName() >> "arch" }
        platform.getOperatingSystem() >> Mock(OperatingSystemInternal) { getName() >> "os" }
        1 * toolChain.select(platform) >> platformToolChain
        1 * platformToolChain.newCompiler({ObjectiveCppPCHCompileSpec.class.isAssignableFrom(it)}) >> objCppPCHCompiler
        1 * objCppPCHCompiler.execute({ ObjectiveCppPCHCompileSpec spec ->
            assert spec.sourceFiles*.name== ["sourceFile"]
            assert spec.args == ['arg']
            assert spec.allArgs == ['arg']
            assert spec.macros == [def: 'value']
            assert spec.objectFileDir.name == "outputFile"
            true
        }) >> result
        1 * result.didWork >> true
        0 * _._

        and:
        objCppPCHCompile.didWork
    }
}
