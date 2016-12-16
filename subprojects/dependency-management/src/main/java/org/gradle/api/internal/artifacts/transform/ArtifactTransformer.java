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

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
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

    ArtifactTransformer(ArtifactTransforms artifactTransforms, ArtifactAttributeMatcher attributeMatcher) {
        this.artifactTransforms = artifactTransforms;
        this.attributeMatcher = attributeMatcher;
    }

    public ArtifactTransformer(ResolutionStrategyInternal resolutionStrategy, AttributesSchema attributesSchema) {
        this.attributeMatcher = new ArtifactAttributeMatcher(attributesSchema);
        this.artifactTransforms = new InstantiatingArtifactTransforms(resolutionStrategy, this.attributeMatcher);
    }

    private boolean matchArtifactsAttributes(HasAttributes candidate, AttributeContainer requested) {
        return attributeMatcher.attributesMatch(candidate, requested, candidate.getAttributes().keySet());
    }

    private Transformer<List<File>, File> getTransform(HasAttributes from, AttributeContainer to) {
        return artifactTransforms.getTransform(from.getAttributes(), to);
    }

    /**
     * Returns a selector that selects the variant matching the supplied attributes, or which can be transformed to match. The selector may return null to mean 'none of these'
     */
    public <T extends HasAttributes> Transformer<T, Collection<? extends T>> variantSelector(final AttributeContainer attributes) {
        return new AttributeMatchingVariantSelector<T>(attributes);
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
            @Override
            public void visitArtifact(final ResolvedArtifact artifact) {
                List<ResolvedArtifact> transformResults = transformedArtifacts.get(Pair.of(artifact, immutableAttributes));
                if (transformResults != null) {
                    for (ResolvedArtifact resolvedArtifact : transformResults) {
                        visitor.visitArtifact(resolvedArtifact);
                    }
                    return;
                }

                final Transformer<List<File>, File> transform = getTransform(artifact, immutableAttributes);
                if (transform == null) {
                    if (matchArtifactsAttributes(artifact, immutableAttributes)) {
                        visitor.visitArtifact(artifact);
                        return;
                    }
                    throw new ArtifactResolveException("Artifact " + artifact + " is not compatible with requested attributes " + immutableAttributes);
                }

                TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

                transformResults = Lists.newArrayList();
                List<File> transformedFiles = transform.transform(artifact.getFile());
                for (final File output : transformedFiles) {
                    ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(artifact.getId().getComponentIdentifier(), output.getName());
                    IvyArtifactName artifactName = DefaultIvyArtifactName.forAttributeContainer(output.getName(), immutableAttributes);
                    ResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(artifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
                    transformResults.add(resolvedArtifact);
                    visitor.visitArtifact(resolvedArtifact);
                }
                transformedArtifacts.put(Pair.of(artifact, immutableAttributes), transformResults);
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
                            Transformer<List<File>, File> transform = getTransform(fileWithAttributes, immutableAttributes);
                            if (transform == null) {
                                if (matchArtifactsAttributes(fileWithAttributes, immutableAttributes)) {
                                    result.add(file);
                                    continue;
                                }
                                continue;
                            }
                            transformResults = transform.transform(file);
                            transformedFiles.put(Pair.of(file, immutableAttributes), transformResults);
                            result.addAll(transformResults);
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
        };
    }

    private class AttributeMatchingVariantSelector<T extends HasAttributes> implements Transformer<T, Collection<? extends T>> {
        private final AttributeContainer attributes;
        private final Map<AttributeContainer, Boolean> previousMatches = Maps.newHashMap();

        public AttributeMatchingVariantSelector(AttributeContainer attributes) {
            this.attributes = attributes;
        }

        @Override
        public String toString() {
            return "Variant selector for " + attributes;
        }

        @Override
        public T transform(Collection<? extends T> variants) {
            // Note: This algorithm is a placeholder only. Should deal with ambiguous matches
            if (attributes.isEmpty()) {
                return variants.iterator().next();
            }

            for (T variant : variants) {
                AttributeContainer variantAttributes = variant.getAttributes();

                Boolean b = previousMatches.get(variantAttributes);
                if (b != null) {
                    if (b) {
                        return variant;
                    } else {
                        continue;
                    }
                }

                // For now, compare only the attributes that are in common
                Set<Attribute<?>> commonAttributes = Sets.newHashSet();
                Set<Attribute<?>> keys = new HashSet<Attribute<?>>(variant.getAttributes().keySet());
                keys.retainAll(attributes.keySet());
                for (Attribute attribute : keys) {
                    commonAttributes.add(attribute);
                }

                boolean matches = attributeMatcher.attributesMatch(variant, attributes, commonAttributes);
                if (matches) {
                    previousMatches.put(variantAttributes, true);
                    return variant;
                }
                if (getTransform(variant, attributes) != null) {
                    previousMatches.put(variantAttributes, true);
                    return variant;
                }
                previousMatches.put(variantAttributes, false);
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AttributeMatchingVariantSelector)) {
                return false;
            }
            AttributeMatchingVariantSelector<?> that = (AttributeMatchingVariantSelector<?>) o;
            return Objects.equal(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(attributes);
        }
    }
}
