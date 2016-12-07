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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.internal.Pair;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.reflect.DirectInstantiator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

class InstantiatingArtifactTransforms implements ArtifactTransforms {
    private final ResolutionStrategyInternal resolutionStrategy;
    private final ArtifactAttributeMatcher attributeMatcher;

    public InstantiatingArtifactTransforms(ResolutionStrategyInternal resolutionStrategy, ArtifactAttributeMatcher attributeMatcher) {
        this.resolutionStrategy = resolutionStrategy;
        this.attributeMatcher = attributeMatcher;
    }

    @Override
    public Pair<Transformer<List<File>, File>, RunnableBuildOperation> getTransform(AttributeContainer from, AttributeContainer to) {
        for (ArtifactTransformRegistrations.ArtifactTransformRegistration transformReg : resolutionStrategy.getTransforms()) {
            if (attributeMatcher.attributesMatch(from, transformReg.from, transformReg.from)
                && attributeMatcher.attributesMatch(to, transformReg.to, transformReg.to)) {
                return createArtifactTransformer(transformReg);
            }
        }
        return null;
    }

    private RunnableBuildOperation contentTransformRunnable(final ArtifactFileTransformer artifactTransformer) {
        return new RunnableBuildOperation() {
            @Override
            public String getDescription() {
                return "Transform from " + artifactTransformer.input + " to " +artifactTransformer.outputs;
            }

            @Override
            public void run() {
                artifactTransformer.artifactTransform.contentTransform();
                for (File output : artifactTransformer.outputs) {
                    if (!output.exists()) {
                        throw new ArtifactTransformException(artifactTransformer.input, artifactTransformer.outputAttributes, artifactTransformer.artifactTransform,
                            new FileNotFoundException("ArtifactTransform output '" + output.getPath() + "' does not exist"));
                    }
                }
            }
        };
    }

    private Pair<Transformer<List<File>, File>, RunnableBuildOperation> createArtifactTransformer(ArtifactTransformRegistrations.ArtifactTransformRegistration registration) {
        ArtifactTransform artifactTransform = DirectInstantiator.INSTANCE.newInstance(registration.type);
        registration.config.execute(artifactTransform);
        ArtifactFileTransformer artifactFileTransformer = new ArtifactFileTransformer(artifactTransform, registration.to);
        RunnableBuildOperation contentTransform = contentTransformRunnable(artifactFileTransformer);
        return Pair.<Transformer<List<File>, File>, RunnableBuildOperation>of(artifactFileTransformer, contentTransform);
    }

    private static class ArtifactFileTransformer implements Transformer<List<File>, File> {
        private final ArtifactTransform artifactTransform;
        private final AttributeContainer outputAttributes;
        private File input;
        private List<File> outputs;

        private ArtifactFileTransformer(ArtifactTransform artifactTransform, AttributeContainer outputAttributes) {
            this.artifactTransform = artifactTransform;
            this.outputAttributes = outputAttributes;
        }

        @Override
        public List<File> transform(File input) {
            this.input = input;
            if (artifactTransform.getOutputDirectory() != null) {
                artifactTransform.getOutputDirectory().mkdirs();
            }
            outputs = doTransform(input);
            if (outputs == null) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new NullPointerException("Illegal null output from ArtifactTransform"));
            }
            return outputs;
        }

        private List<File> doTransform(File input) {
            try {
                return artifactTransform.transform(input, outputAttributes);
            } catch (Exception e) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, e);
            }
        }
    }

}
