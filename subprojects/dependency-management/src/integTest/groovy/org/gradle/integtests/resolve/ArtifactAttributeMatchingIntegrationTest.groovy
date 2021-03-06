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

package org.gradle.integtests.resolve

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Unroll

class ArtifactAttributeMatchingIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setupWith(String requiredAttributes, boolean transformOnConsumerSide, boolean useView, String expect) {
        settingsFile << """
            rootProject.name = 'root'
            include 'producer'
            include 'consumer'
        """

        buildFile << """
            def flavor = Attribute.of('flavor', String)
            def variant = Attribute.of('variant', String)
            def required = Attribute.of('required', String)
            
            class VariantArtifactTransform extends ArtifactTransform {

                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('variant', String), "variant1")

                    targets.newTarget().attribute(Attribute.of('variant', String), "variant2")
                }

                List<File> transform(File input, AttributeContainer target) {
                    println this.class.name
                    def output = new File(input.parentFile, "producer.variant2")
                    input.parentFile.mkdirs()
                    output << "transformed"
                    return [output]         
                }
            }
            
            project(':producer') {
                configurations {
                    producerConfiguration {
                        attributes { attribute (flavor, 'flavor1') }
                    }
                }
                task variant1 {
                    outputs.file('producer.variant1')
                }
                task variant2 {
                    outputs.file('producer.variant2')
                }
            }
            
            project(':consumer') {
                configurations {
                    consumerConfiguration {
                        attributes { attribute ( flavor, 'flavor1') }
                    }
                }
                dependencies {
                    consumerConfiguration project(':producer')
                }
            }
        """
        if (transformOnConsumerSide) {
            buildFile << """
                project(':producer') {
                    configurations {
                        producerConfiguration {
                            outgoing {
                                variants {
                                    variant1 {
                                        artifact file: file('producer.variant1'), builtBy: tasks.variant1
                                        attributes { attribute (variant, 'variant1') }
                                    }
                                }
                            }
                        }
                    }
                }
                project(':consumer') {
                    dependencies {
                        registerTransform(VariantArtifactTransform) {}
                    }
                }
            """
        } else {
            buildFile << """
                project(':producer') {     
                    configurations {
                        producerConfiguration {
                            outgoing {
                                variants {
                                    variant1 {
                                        artifact file: file('producer.variant1'), builtBy: tasks.variant1
                                        attributes { attribute (variant, 'variant1') }
                                    }
                                    variant2 {
                                        artifact file: file('producer.variant2'), builtBy: tasks.variant2
                                        attributes  { attribute (variant, 'variant2') }
                                    }
                                }
                            }
                        }
                    }
                }
            """
        }

        if (useView) {
            buildFile << """
                project(':consumer') {
                    task resolve {
                        def files = configurations.consumerConfiguration.incoming.artifactView().attributes{$requiredAttributes}.files
                        inputs.files files
                        doLast {
                            assert files.collect { it.name } == $expect
                        }
                    }
                }
            """
        } else {
            buildFile << """
                project(':consumer') {
                    task resolve {
                        def files = configurations.consumerConfiguration.incoming.getFiles()
                        inputs.files files
                        doLast {
                            assert files.collect { it.name } == $expect
                        }
                    }
                }
            """
        }

    }

    @Unroll
    def "can filter for variant artifacts with useTransform=#useTransformOnConsumerSide and useView=#useView"() {
        given:
        setupWith("it.attribute(variant, 'variant2')", useTransformOnConsumerSide, useView, useView ? "['producer.variant2']"
            : "['producer.variant1']") //TODO should throw ambiguity error, see DefaultArtifactTransforms.AttributeMatchingVariantSelector

        buildFile << """
            project(':producer') {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                    }
                }
            }
            project(':consumer') {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(variant)
                    }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        executedTasks.unique().sort() == [':consumer:resolve', (useTransformOnConsumerSide || !useView? ':producer:variant1' : ':producer:variant2')]
        executedTransforms            == (!useTransformOnConsumerSide || !useView ? [] : ['VariantArtifactTransform'])

        where:
        useTransformOnConsumerSide | useView
        false                      | false
        false                      | true
        true                       | false
        true                       | true
    }

    @Unroll
    def "uses same attributes and compatibility rules in configurations and variants for variant=#variant with useTransform=#useTransformOnConsumerSide and useView=#useView"() {
        given:
        setupWith("it.attribute(variant, '$variant')", useTransformOnConsumerSide, useView, "['producer.${variant.toLowerCase()}', 'producer2.${variant.toLowerCase()}']")
        settingsFile << """
            include 'producer2'
        """

        String variantToMatchViaConfiguration = variant.toLowerCase()

        buildFile << """
            project(':producer') {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                    }
                }
            }
            project(':consumer') {
                configurations {
                    consumerConfiguration {
                        attributes { attribute(variant, '$variantToMatchViaConfiguration') }
                    }
                }
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(variant) {
                            compatibilityRules.add { details ->
                                if (details.consumerValue.toLowerCase() == details.producerValue.toLowerCase()) {
                                    details.compatible()
                                }
                            }
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                    consumerConfiguration project(':producer2')
                }
            }    
            project(':producer2') {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(variant)
                    }
                }
                configurations {
                    producer2Variant1Configuration {
                        attributes { attribute (flavor, 'flavor1') }
                        attributes { attribute (variant, 'variant1') }
                    }
                    producer2Variant2Configuration {
                        attributes { attribute (flavor, 'flavor1') }
                        attributes { attribute (variant, 'variant2') }
                    }
                }
                task variant1 {
                    outputs.file('producer2.variant1')
                }
                task variant2 {
                    outputs.file('producer2.variant2')
                }
                artifacts {
                    producer2Variant1Configuration file: file('producer2.variant1'), builtBy: variant1
                    producer2Variant2Configuration file: file('producer2.variant2'), builtBy: variant2
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        executedTasks.unique().sort() == [':consumer:resolve', ":producer2:${variant.toLowerCase()}", (useTransformOnConsumerSide ? ':producer:variant1' : ":producer:${variant.toLowerCase()}")]
        executedTransforms            == (useTransformOnConsumerSide && variant.toLowerCase() == "variant2" ? ['VariantArtifactTransform'] : [])

        where:
        variant    | useTransformOnConsumerSide | useView
        'variant1' | false                      | false
        'variant1' | false                      | true
        'variant1' | true                       | false
        'variant1' | true                       | true
        //'variant2' | false                      | false -- TODO should throw ambiguity error, see DefaultArtifactTransforms.AttributeMatchingVariantSelector
        'variant2' | false                      | true
        //'variant2' | true                       | false -- TODO should throw ambiguity error, see DefaultArtifactTransforms.AttributeMatchingVariantSelector
        'variant2' | true                       | true
        'VARIANT1' | false                      | true
        'VARIANT1' | true                       | true
        'VARIANT2' | false                      | true
        'VARIANT2' | true                       | true
    }

    @Unroll
    def "honors consumer's assumeCompatibleWhenMissing=#assumeCompatibleWhenMissing with useView=#useView"() {
        given:
        setupWith("it.attribute(variant, 'variant2'); it.attribute(required, 'thisValueIsRequired')", false, useView, assumeCompatibleWhenMissing ? "['producer.variant2']" : "[]")

        String assumeCompatibleWhenMissingRequiredAttribute = assumeCompatibleWhenMissing ? "compatibilityRules.assumeCompatibleWhenMissing()" : ""

        buildFile << """
            project(':producer') {     
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(required)
                    }
                }
            }
            project(':consumer') {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(variant)
                        attribute(required){
                            $assumeCompatibleWhenMissingRequiredAttribute
                        }
                    }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        executedTasks.unique().sort() == (assumeCompatibleWhenMissing ? [':consumer:resolve', ':producer:variant2'] : [':consumer:resolve'])
        executedTransforms            == []

        where:
        assumeCompatibleWhenMissing | useView
        false                       | true
        true                        | true
    }

    @Unroll
    @NotYetImplemented //ComponentAttributeMatcher.isMatching() currently does not have access to the producer schema and uses the consumer schema for everything
    def "honors producer's assumeCompatibleWhenMissing=#assumeCompatibleWhenMissing with useView=#useView"() {
        given:
        setupWith("it.attribute(variant, 'variant2')", false, useView, assumeCompatibleWhenMissing ? "['producer.variant2']" : "[]")

        String assumeCompatibleWhenMissingRequiredAttribute = assumeCompatibleWhenMissing ? "compatibilityRules.assumeCompatibleWhenMissing()" : ""

        buildFile << """
            project(':producer') {
                configurations {
                    producerConfiguration {
                        outgoing {
                            variants {
                                variant2 {
                                    attributes { attribute (required, 'thisValueIsRequired') }
                                }
                            }
                        }
                    }
                }
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(variant)
                        attribute(required) {
                            $assumeCompatibleWhenMissingRequiredAttribute
                        }
                    }
                }
            }
            project(':consumer') {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(variant)
                    }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        executedTasks.unique().sort() == (assumeCompatibleWhenMissing ? [':consumer:resolve', ':producer:variant2'] : [':consumer:resolve'])
        executedTransforms            == []

        where:
        assumeCompatibleWhenMissing | useView
        false                       | true
        true                        | true
    }

    private List<String> getExecutedTransforms() {
        output.readLines().findAll { it == "VariantArtifactTransform" }
    }
}
