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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

public class BuildProgressLogger implements LoggerProvider {
    public static final String INITIALIZATION_PHASE_DESCRIPTION = "INITIALIZATION PHASE";
    public static final String INITIALIZATION_PHASE_SHORT_DESCRIPTION = "INITIALIZING";
    public static final String CONFIGURATION_PHASE_DESCRIPTION = "CONFIGURATION PHASE";
    public static final String CONFIGURATION_PHASE_SHORT_DESCRIPTION = "CONFIGURING";
    public static final String EXECUTION_PHASE_DESCRIPTION = "EXECUTION PHASE";
    public static final String EXECUTION_PHASE_SHORT_DESCRIPTION = "EXECUTING";
    public static final int PROGRESS_BAR_WIDTH = 13;
    public static final String PROGRESS_BAR_PREFIX = "<";
    public static final char PROGRESS_BAR_COMPLETE_CHAR = '=';
    public static final char PROGRESS_BAR_INCOMPLETE_CHAR = '-';
    public static final String PROGRESS_BAR_SUFFIX = ">";
    public static final String CONFIGURING_PROJECTS = "Configuring projects";

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
        // TODO(ew): consider how to show buildSrc progress — it seems like a separate build
        // TODO(ew): test this with composite builds
        buildProgressFormatter = newProgressBar(INITIALIZATION_PHASE_SHORT_DESCRIPTION, 1);
        buildProgress = loggerProvider.start(INITIALIZATION_PHASE_DESCRIPTION, buildProgressFormatter.getProgress());
    }

    public void settingsEvaluated() {
        buildProgress.completed();
    }

    public void projectsLoaded(int totalProjects) {
        buildProgressFormatter = newProgressBar(CONFIGURATION_PHASE_SHORT_DESCRIPTION, totalProjects);
        buildProgress = loggerProvider.start(CONFIGURATION_PHASE_DESCRIPTION, buildProgressFormatter.getProgress());
    }

    // TODO(ew): show work in progress in overall build status
    public void beforeEvaluate(String projectPath) {}

    public void afterEvaluate(String projectPath) {
        buildProgress.progress(buildProgressFormatter.incrementAndGetProgress());
    }

    public void graphPopulated(int totalTasks) {
        buildProgress.completed();
        buildProgressFormatter = newProgressBar(EXECUTION_PHASE_SHORT_DESCRIPTION, totalTasks);
        buildProgress = loggerProvider.start(EXECUTION_PHASE_DESCRIPTION, buildProgressFormatter.getProgress());
    }

    // TODO(ew): show work-in-progress in overall build status
    public void beforeExecute() {}

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

    @VisibleForTesting
    public ProgressBar newProgressBar(String initialSuffix, int totalWorkItems) {
        return new ProgressBar(PROGRESS_BAR_PREFIX,
            PROGRESS_BAR_WIDTH,
            PROGRESS_BAR_SUFFIX,
            PROGRESS_BAR_COMPLETE_CHAR,
            PROGRESS_BAR_INCOMPLETE_CHAR,
            initialSuffix,
            totalWorkItems);
    }
}
