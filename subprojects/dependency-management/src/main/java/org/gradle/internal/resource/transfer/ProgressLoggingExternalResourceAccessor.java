/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.progress.BuildOperationFinishHandle;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class ProgressLoggingExternalResourceAccessor extends AbstractProgressLoggingHandler implements ExternalResourceAccessor {
    private final ExternalResourceAccessor delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public ProgressLoggingExternalResourceAccessor(ExternalResourceAccessor delegate, ProgressLoggerFactory progressLoggerFactory, BuildOperationExecutor buildOperationExecutor) {
        super(progressLoggerFactory);
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public ExternalResourceReadResponse openResource(final URI location, final boolean revalidate) {
        ExternalResourceMetaData metaData = delegate.getMetaData(location, revalidate);
        DownloadBuildOperationDescriptor downloadBuildOperationDescriptor = new DownloadBuildOperationDescriptor(metaData.getLocation(), metaData.getContentLength(), metaData.getContentType());
        BuildOperationDetails buildOperationDetails = BuildOperationDetails.displayName("Download NB" + metaData.getLocation().toString()).parent(buildOperationExecutor.getCurrentOperation()).operationDescriptor(downloadBuildOperationDescriptor).build();

        BuildOperationFinishHandle<ExternalResourceReadResponse> buildOperationHandle = buildOperationExecutor.start(buildOperationDetails, new Transformer<ExternalResourceReadResponse, BuildOperationContext>() {
            @Override
            public ExternalResourceReadResponse transform(BuildOperationContext context) {
                return delegate.openResource(location, revalidate);
            }
        });

        if (buildOperationHandle.get() != null) {
            return new ProgressLoggingExternalResource(location, new BuildOperationAwareExternalResource(buildOperationHandle));
        } else {
            buildOperationHandle.finish(new Action<BuildOperationContext>() {
                @Override
                public void execute(BuildOperationContext context) {

                }
            });
            return null;
        }
    }

    @Nullable
    public ExternalResourceMetaData getMetaData(URI location, boolean revalidate) {
        return delegate.getMetaData(location, revalidate);
    }

    private class ProgressLoggingExternalResource implements ExternalResourceReadResponse {
        private final ExternalResourceReadResponse resource;
        private final ResourceOperation downloadOperation;

        private ProgressLoggingExternalResource(URI location, ExternalResourceReadResponse resource) {
            this.resource = resource;
            downloadOperation = createResourceOperation(location.toString(), ResourceOperation.Type.download, getClass(), resource.getMetaData().getContentLength());
        }

        @Override
        public InputStream openStream() throws IOException {
            return new ProgressLoggingInputStream(resource.openStream(), downloadOperation);
        }

        public void close() throws IOException {
            try {
                resource.close();
            } finally {
                downloadOperation.completed();
            }
        }

        @Nullable
        public ExternalResourceMetaData getMetaData() {
            return resource.getMetaData();
        }

        public boolean isLocal() {
            return resource.isLocal();
        }

        public String toString(){
            return resource.toString();
        }
    }

}
