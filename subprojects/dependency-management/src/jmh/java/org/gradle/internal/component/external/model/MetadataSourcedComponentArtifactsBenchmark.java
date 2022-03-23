/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Named;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.type.ArtifactTypeContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.transform.ArtifactTransforms;
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder;
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransforms;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver;
import org.gradle.api.internal.artifacts.transform.Transformation;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeRegistry;
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.model.InstantiatorBackedObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.api.specs.Specs;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.internal.component.external.model.maven.DefaultMutableMavenModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@State(Scope.Benchmark)
public class MetadataSourcedComponentArtifactsBenchmark {
    private ExcludeSpec exclusions;
    private Map<ComponentArtifactIdentifier, ResolvableArtifact> resolvedArtifactCache;

    private CalculatedValueContainerFactory calculatedValueContainerFactory;
    private ArtifactResolver artifactResolver;

    private ConfigurationMetadata variantFromGraph;
    private ComponentResolveMetadata componentResolveMetadata;
    private ArtifactTypeRegistry artifactTypeRegistry;
    private ArtifactTransforms transforms;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        exclusions = new DefaultExcludeFactory().nothing();
        resolvedArtifactCache = new HashMap<>();

        calculatedValueContainerFactory = null; // this should never be used
        artifactResolver = null; // this should never be used

        NamedObjectInstantiator objectInstantiator = new NamedObjectInstantiator(new DefaultCrossBuildInMemoryCacheFactory(new ListenerManager() {
            @Override
            public void addListener(Object listener) {

            }

            @Override
            public void removeListener(Object listener) {

            }

            @Override
            public <T> boolean hasListeners(Class<T> listenerClass) {
                return false;
            }

            @Override
            public <T> T getBroadcaster(Class<T> listenerClass) {
                return null;
            }

            @Override
            public <T> AnonymousListenerBroadcast<T> createAnonymousBroadcaster(Class<T> listenerClass) {
                return null;
            }

            @Override
            public void useLogger(Object logger) {

            }
        }));
        DefaultMavenImmutableAttributesFactory attributesFactory = new DefaultMavenImmutableAttributesFactory(new DefaultImmutableAttributesFactory(new TestIsolatableFactory(), objectInstantiator), objectInstantiator);
        artifactTypeRegistry = new DefaultArtifactTypeRegistry(DirectInstantiator.INSTANCE, attributesFactory, CollectionCallbackActionDecorator.NOOP, null /* unused */);
        ArtifactTypeContainer artifactTypes = artifactTypeRegistry.create();
        ObjectFactory objectFactory = new InstantiatorBackedObjectFactory(DirectInstantiator.INSTANCE) {
            @Override
            public <T extends Named> T named(Class<T> type, String name) throws ObjectInstantiationException {
                return objectInstantiator.named(type, name);
            }
        };

        artifactTypes.create(ArtifactTypeDefinition.JAR_TYPE).getAttributes()
                .attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME))
                .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR));

        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId("org", "name", "1.0");
        ModuleComponentIdentifier componentId = DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier);
        MutableMavenModuleResolveMetadata mavenMetadata = new DefaultMutableMavenModuleResolveMetadata(moduleVersionIdentifier, componentId, Collections.emptyList(), attributesFactory, objectInstantiator, EmptySchema.INSTANCE, ImmutableMap.of());
        mavenMetadata.addVariant("runtime", ImmutableAttributes.EMPTY);
        mavenMetadata.addVariant("compile", ImmutableAttributes.EMPTY);
        mavenMetadata.addVariant("sources", ImmutableAttributes.EMPTY);
        mavenMetadata.addVariant("javadoc", ImmutableAttributes.EMPTY);
        componentResolveMetadata = mavenMetadata.asImmutable();
        variantFromGraph = new DefaultConfigurationMetadata(componentId, "graph", true, true, ImmutableSet.of(), ImmutableList.of(), VariantMetadataRules.noOp(), ImmutableList.of(), ImmutableAttributes.EMPTY, false);

        transforms = new DefaultArtifactTransforms(new ConsumerProvidedVariantFinder(null, EmptySchema.INSTANCE, attributesFactory), EmptySchema.INSTANCE, attributesFactory, null);
    }

    @Setup(Level.Iteration)
    public void setupInstance() {
        resolvedArtifactCache.clear();
    }

    @Benchmark
    public void benchmarkGetArtifactsFor(Blackhole blackhole) {
        ArtifactSet artifactSet = new MetadataSourcedComponentArtifacts().getArtifactsFor(
                componentResolveMetadata,
                variantFromGraph,
                artifactResolver,
                resolvedArtifactCache,
                artifactTypeRegistry,
                exclusions,
                ImmutableAttributes.EMPTY,
                calculatedValueContainerFactory);
        blackhole.consume(artifactSet);
    }
//
//    @Benchmark
//    public void benchmarkSelectArtifacts(Blackhole blackhole) {
//        ArtifactSet artifactSet = new MetadataSourcedComponentArtifacts().getArtifactsFor(
//                componentResolveMetadata,
//                variantFromGraph,
//                artifactResolver,
//                resolvedArtifactCache,
//                artifactTypeRegistry,
//                exclusions,
//                ImmutableAttributes.EMPTY,
//                calculatedValueContainerFactory);
//        ResolvedArtifactSet resolvedArtifactSet = artifactSet.select(Specs.SATISFIES_ALL, transforms.variantSelector(ImmutableAttributes.EMPTY, false, new ExtraExecutionGraphDependenciesResolverFactory() {
//            @Override
//            public TransformUpstreamDependenciesResolver create(ComponentIdentifier componentIdentifier, Transformation transformation) {
//                return null;
//            }
//        }));
//        blackhole.consume(resolvedArtifactSet);
//    }

    private static class TestIsolatableFactory implements IsolatableFactory {
        @Override
        public <T> Isolatable<T> isolate(T value) {
            return new Isolatable<T>() {
                @Override
                public T isolate() {
                    return value;
                }

                @Override
                public ValueSnapshot asSnapshot() {
                    throw new UnsupportedOperationException();
                }

                @Nullable
                @Override
                public <S> S coerce(Class<S> type) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void appendToHasher(Hasher hasher) {
                    hasher.putString(String.valueOf(value));
                }
            };
        }
    }
}
