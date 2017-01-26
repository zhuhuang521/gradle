/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.progress;

import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

public class BuildProgressLogger implements LoggerProvider {

    private final ProgressLoggerProvider loggerProvider;

    private ProgressLogger buildProgress;
    private ProgressFormatter buildProgressFormatter;

    // TODO(ew): consider if/how to maintain a separate overall build progress from progress of workers
    // We want to decouple this from DefaultGradleLauncherFactory (it should only know about one BuildProgressLogger)

    public BuildProgressLogger(ProgressLoggerFactory progressLoggerFactory) {
        this(new ProgressLoggerProvider(progressLoggerFactory, BuildProgressLogger.class));
    }

    BuildProgressLogger(ProgressLoggerProvider loggerProvider) {
        this.loggerProvider = loggerProvider;
    }

    public void buildStarted() {
        // TODO(ew): consider how to show buildSrc progress
        buildProgress = loggerProvider.start("INITIALIZATION PHASE", "INITIALIZING");
    }

    public void settingsEvaluated() {
        buildProgress.completed();
    }

    public void projectsLoaded(int totalProjects) {
        buildProgressFormatter = new ProgressBar(13, '=', "CONFIGURING", totalProjects);
        buildProgress = loggerProvider.start("CONFIGURATION PHASE", buildProgressFormatter.getProgress());
    }

    public void beforeEvaluate(String projectPath) {
        // TODO(ew): show work in progress
        // TODO(ew): show "> projectPath" in parallel progress
    }

    public void afterEvaluate(String projectPath) {
        buildProgress.progress(buildProgressFormatter.incrementAndGetProgress());
        // TODO(ew): show "> projectPath" in parallel progress
    }

    public void projectsEvaluated() {
        // TODO(ew): investigate usefulness of this
    }

    public void graphPopulated(int totalTasks) {
        buildProgress.completed();
        buildProgressFormatter = new ProgressBar(13, '=', "EXECUTING", totalTasks);
        buildProgress = loggerProvider.start("EXECUTION PHASE", buildProgressFormatter.getProgress());
    }

    public void beforeExecute() {
        // TODO(ew): show work-in-progress
    }

    public void afterExecute() {
        buildProgress.progress(buildProgressFormatter.incrementAndGetProgress());
    }

    public void buildFinished() {
        buildProgress.completed();
        buildProgress = null;
        buildProgressFormatter = null;
    }

    public ProgressLogger getLogger() {
        if (buildProgress == null) {
            throw new IllegalStateException("Build logger is unavailable (it hasn't started or is already completed).");
        }
        return buildProgress;
    }
}
