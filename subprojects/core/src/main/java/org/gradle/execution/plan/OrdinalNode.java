/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.resources.ResourceLock;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * Represents a node in the graph that controls ordinality of destroyers and producers as they are
 * added to the task graph.  For example "clean build" on the command line implies that the user wants
 * to run the clean tasks of each project before the build tasks of each project.  Ordinal nodes ensure
 * this order by tracking the dependencies of destroyers and producers in each group of tasks added to
 * the task graph and prevents producers of a higher ordinality to run before the destroyers of a lower
 * ordinality even if the destroyers are delayed waiting on dependencies (and vice versa).
 */
public class OrdinalNode extends Node implements SelfExecutingNode {
    public enum Type { DESTROYER, PRODUCER }

    private final Type type;
    private final int ordinal;

    public OrdinalNode(Type type, int ordinal) {
        this.type = type;
        this.ordinal = ordinal;
    }

    @Nullable
    @Override
    public Throwable getNodeFailure() {
        return null;
    }

    @Override
    public void rethrowNodeFailure() { }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) { }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        return null;
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        return null;
    }

    @Override
    public List<? extends ResourceLock> getResourcesToLock() {
        return Collections.emptyList();
    }

    @Override
    // TODO is there a better term to use here than "task group"
    public String toString() {
        return type.name().toLowerCase() + " locations for task group " + ordinal;
    }

    @Override
    public int compareTo(Node o) {
        return -1;
    }

    @Override
    public void execute(NodeExecutionContext context) { }

    public Type getType() {
        return type;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void addDependenciesFrom(TaskNode taskNode) {
        // Only add hard successors that will actually be executed
        Stream<Node> executingSuccessors = stream(taskNode.getHardSuccessors().spliterator(), false).filter(Node::isRequired);
        executingSuccessors.forEach(this::addDependencySuccessor);
    }
}
