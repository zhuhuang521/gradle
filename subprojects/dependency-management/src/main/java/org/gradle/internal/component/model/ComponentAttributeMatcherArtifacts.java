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

package org.gradle.internal.component.model;

import org.gradle.api.GradleException;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.CompatibilityRuleChainInternal;
import org.gradle.internal.Cast;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ComponentAttributeMatcherArtifacts {

    private static boolean doMatch(AttributesSchema consumerAttributeSchema, AttributesSchema producerAttributeSchema, HasAttributes candidates, AttributeContainer consumerAttributesContainer, AttributeContainer attributesToConsider) {
        AttributeContainer producerAttributesContainer = candidates.getAttributes();
        Set<Attribute<Object>> allAttributes = Cast.uncheckedCast(attributesToConsider.keySet());
        for (Attribute<Object> attribute : allAttributes) {
            AttributeValue<Object> consumerValue = attributeValue(attribute, consumerAttributeSchema, consumerAttributesContainer);
            AttributeValue<Object> producerValue = attributeValue(attribute, producerAttributeSchema, producerAttributesContainer);

            if (!update(attribute, consumerAttributeSchema, producerAttributeSchema, consumerValue, producerValue)) {
                return false;
            }
        }
        return true;
    }

    private static AttributeValue<Object> attributeValue(Attribute<Object> attribute, AttributesSchema schema, AttributeContainer container) {
        AttributeValue<Object> attributeValue;
        if (schema.hasAttribute(attribute)) {
            attributeValue = container.contains(attribute) ? AttributeValue.of(container.getAttribute(attribute)) : AttributeValue.missing();
        } else {
            attributeValue = AttributeValue.unknown();
        }
        return attributeValue;
    }

    public static boolean match(AttributesSchema consumerAttributeSchema, AttributesSchema producerAttributeSchema,
                                HasAttributes candidates, //configAttributes + artifactAttributes
                                AttributeContainer consumerAttributesContainer,
                                AttributeContainer attributesToConsider) {

        return doMatch(consumerAttributeSchema, producerAttributeSchema, candidates, consumerAttributesContainer, attributesToConsider);
    }

    private static boolean update(final Attribute<Object> attribute, final AttributesSchema consumerSchema, final AttributesSchema producerSchema, final AttributeValue<Object> consumerValue, final AttributeValue<Object> producerValue) {
        AttributesSchema schemaToUse = consumerSchema;
        boolean missingOrUnknown = false;
        if (consumerValue.isUnknown() || consumerValue.isMissing()) {
            // We need to use the producer schema in this case
            schemaToUse = producerSchema;
            missingOrUnknown = true;
        } else if (producerValue.isUnknown() || producerValue.isMissing()) {
            missingOrUnknown = true;
        }
        AttributeMatchingStrategy<Object> strategy = schemaToUse.getMatchingStrategy(attribute);
        CompatibilityRuleChainInternal<Object> compatibilityRules = (CompatibilityRuleChainInternal<Object>) strategy.getCompatibilityRules();
        if (missingOrUnknown) {
            if (compatibilityRules.isCompatibleWhenMissing()) {
                if (producerValue.isPresent()) {
                    return true;
                }
            }
            return false;
        }
        final Set<Boolean> compatible = new HashSet<Boolean>(1);
        CompatibilityCheckDetails<Object> details = new CompatibilityCheckDetails<Object>() {

            @Override
            public Object getConsumerValue() {
                return consumerValue.get();
            }

            @Override
            public Object getProducerValue() {
                return producerValue.get();
            }

            @Override
            public void compatible() {
                compatible.clear();
                compatible.add(true);
            }

            @Override
            public void incompatible() {
                compatible.clear();
                compatible.add(false);
            }
        };
        try {
            compatibilityRules.execute(details);
            Iterator<Boolean> iterator = compatible.iterator();
            return iterator.hasNext() ? iterator.next() : false;
        } catch (Exception ex) {
            throw new GradleException("Unexpected error thrown when trying to match attribute values with " + strategy, ex);
        }
    }
}
