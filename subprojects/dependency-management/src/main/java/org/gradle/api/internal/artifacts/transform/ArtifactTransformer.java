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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultAttributeContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.resolve.ArtifactResolveException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArtifactTransformer {
    private final ArtifactTransforms artifactTransforms;
    private final ArtifactAttributeMatcher attributeMatcher;
    private final Map<Pair<File, AttributeContainer>, List<File>> transformedFiles = Maps.newHashMap();
    private final Map<Pair<ResolvedArtifact, AttributeContainer>, List<ResolvedArtifact>> transformedArtifacts = Maps.newHashMap();
    private final BuildOperationProcessor buildOperationProcessor;

    public ArtifactTransformer(ArtifactTransforms artifactTransforms, ArtifactAttributeMatcher attributeMatcher, BuildOperationProcessor buildOperationProcessor) {
        this.artifactTransforms = artifactTransforms;
        this.attributeMatcher = attributeMatcher;
        this.buildOperationProcessor = buildOperationProcessor;
    }

    public ArtifactTransformer(ResolutionStrategyInternal resolutionStrategy, AttributesSchema attributesSchema, BuildOperationProcessor buildOperationProcessor) {
        this.attributeMatcher = new ArtifactAttributeMatcher(attributesSchema);
        this.artifactTransforms = new InstantiatingArtifactTransforms(resolutionStrategy, this.attributeMatcher);
        this.buildOperationProcessor = buildOperationProcessor;
    }

    private boolean matchArtifactsAttributes(HasAttributes candidate, AttributeContainer requested) {
        return attributeMatcher.attributesMatch(candidate, requested, candidate.getAttributes());
    }

    private Pair<Transformer<List<File>, File>, RunnableBuildOperation> getTransform(HasAttributes from, AttributeContainer to) {
        return artifactTransforms.getTransform(from.getAttributes(), to);
    }

    /**
     * Returns a selector that selects the variant matching the supplied attributes, or which can be transformed to match. The selector may return null to mean 'none of these'
     */
    public <T extends HasAttributes> Transformer<T, Collection<? extends T>> variantSelector(final AttributeContainer attributes) {
        if (attributes.isEmpty()) {
            return new Transformer<T, Collection<? extends T>>() {
                @Override
                public T transform(Collection<? extends T> variants) {
                    // Note: This algorithm is a placeholder only. Should deal with ambiguous matches
                    return variants.iterator().next();
                }
            };
        }
        return new Transformer<T, Collection<? extends T>>() {
            @Override
            public T transform(Collection<? extends T> variants) {
                // Note: This algorithm is a placeholder only. Should deal with ambiguous matches
                T canTransform = null;
                for (T variant : variants) {

                    // For now, compare only the attributes that are in common
                    DefaultAttributeContainer commonAttributes = new DefaultAttributeContainer();
                    Set<Attribute<?>> keys = new HashSet<Attribute<?>>(variant.getAttributes().keySet());
                    keys.retainAll(attributes.keySet());
                    for (Attribute attribute : keys) {
                        commonAttributes.attribute(attribute, variant.getAttributes().getAttribute(attribute));
                    }

                    boolean matches = attributeMatcher.attributesMatch(variant, attributes, commonAttributes);
                    if (matches) {
                        return variant;
                    }
                    if (getTransform(variant, attributes) != null) {
                        canTransform = variant;
                    }
                }
                return canTransform;
            }
        };
    }

    /**
     * Returns a visitor that transforms files and artifacts to match the requested attributes
     * and then forwards the results to the given visitor.
     */
    public ArtifactVisitor visitor(final ArtifactVisitor visitor, @Nullable AttributeContainerInternal attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return visitor;
        }
        final AttributeContainer immutableAttributes = attributes.asImmutable();
        return new ArtifactVisitor() {
            public List<RunnableBuildOperation> contentTransforms = Lists.newArrayList();

            @Override
            public void visitArtifact(final ResolvedArtifact artifact) {
                List<ResolvedArtifact> transformResults = transformedArtifacts.get(Pair.of(artifact, immutableAttributes));
                if (transformResults != null) {
                    for (ResolvedArtifact resolvedArtifact : transformResults) {
                        visitor.visitArtifact(resolvedArtifact);
                    }
                    return;
                }

                final Pair<Transformer<List<File>, File>, RunnableBuildOperation> transform = getTransform(artifact, immutableAttributes);
                if (transform == null) {
                    if (matchArtifactsAttributes(artifact, immutableAttributes)) {
                        visitor.visitArtifact(artifact);
                        return;
                    }
                    throw new ArtifactResolveException("Artifact " + artifact + " is not compatible with requested attributes " + immutableAttributes);
                }

                TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

                transformResults = Lists.newArrayList();
                List<File> transformedFiles = transform.getLeft().transform(artifact.getFile());
                for (final File output : transformedFiles) {
                    ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(artifact.getId().getComponentIdentifier(), output.getName());
                    IvyArtifactName artifactName = DefaultIvyArtifactName.forAttributeContainer(output.getName(), immutableAttributes);
                    ResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(artifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
                    transformResults.add(resolvedArtifact);
                    visitor.visitArtifact(resolvedArtifact);
                }
                transformedArtifacts.put(Pair.of(artifact, immutableAttributes), transformResults);
                contentTransforms.add(transform.getRight());
            }

            @Override
            public boolean includeFiles() {
                return visitor.includeFiles();
            }

            @Override
            public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
                List<File> result = new ArrayList<File>();
                RuntimeException transformException = null;
                try {
                    for (File file : files) {
                        try {
                            List<File> transformResults = transformedFiles.get(Pair.of(file, immutableAttributes));
                            if (transformResults != null) {
                                result.addAll(transformResults);
                                continue;
                            }

                            HasAttributes fileWithAttributes = DefaultArtifactAttributes.forFile(file);
                            Pair<Transformer<List<File>, File>, RunnableBuildOperation> transform = getTransform(fileWithAttributes, immutableAttributes);
                            if (transform == null) {
                                if (matchArtifactsAttributes(fileWithAttributes, immutableAttributes)) {
                                    result.add(file);
                                    continue;
                                }
                                continue;
                            }
                            transformResults = transform.getLeft().transform(file);
                            transformedFiles.put(Pair.of(file, immutableAttributes), transformResults);
                            result.addAll(transformResults);
                            contentTransforms.add(transform.getRight());
                        } catch (RuntimeException e) {
                            transformException = e;
                            break;
                        }
                    }
                } catch (Throwable t) {
                    //TODO JJ: this lets the wrapped visitor report issues during file access
                    visitor.visitFiles(componentIdentifier, files);
                }
                if (transformException != null) {
                    throw transformException;
                }
                if (!result.isEmpty()) {
                    visitor.visitFiles(componentIdentifier, result);
                }
            }

            @Override
            public void waitForWorkToFinish() {
                buildOperationProcessor.run(new Action<BuildOperationQueue<RunnableBuildOperation>>() {
                    @Override
                    public void execute(BuildOperationQueue<RunnableBuildOperation> buildOperationQueue) {
                        for (RunnableBuildOperation transform : contentTransforms) {
                            buildOperationQueue.add(transform);
                        }
                    }
                });
            }
        };
    }
}
