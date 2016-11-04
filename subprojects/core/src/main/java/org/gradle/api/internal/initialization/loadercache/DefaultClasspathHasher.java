/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.collections.DefaultDirectoryWalkerFactory;
import org.gradle.api.internal.file.collections.DirectoryWalker;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultClasspathHasher implements ClasspathHasher {

    private final FileHasher fileHasher;
    private final Factory<DirectoryWalker> directoryWalkerFactory;

    public DefaultClasspathHasher(FileHasher fileHasher) {
        this.fileHasher = fileHasher;
        this.directoryWalkerFactory = new DefaultDirectoryWalkerFactory(JavaVersion.current(), FileSystems.getDefault());
    }

    @Override
    public HashCode hash(ClassPath classpath) {
        Hasher checksum = Hashing.md5().newHasher();
        for (File nonCanonicalFile : classpath.getAsFiles()) {
            File file = FileUtils.canonicalize(nonCanonicalFile);
            if (file.isDirectory()) {
                final List<FileVisitDetails> fileEntries = Lists.newArrayList();
                FileVisitor visitor = new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails) {
                        fileEntries.add(fileDetails);
                    }
                };
                directoryWalkerFactory.create().walkDir(file, RelativePath.EMPTY_ROOT, visitor, Specs.satisfyAll(), new AtomicBoolean(), false);
                Collections.sort(fileEntries, new Comparator<FileVisitDetails>() {
                    @Override
                    public int compare(FileVisitDetails o1, FileVisitDetails o2) {
                        return o1.getFile().compareTo(o2.getFile());
                    }
                });
                for (FileVisitDetails fileDetails : fileEntries) {
                    hashFile(checksum, fileDetails.getFile());
                }
            } else if (file.isFile()) {
                // Ignore the path of root files
                hashFile(checksum, file);
            }
            //else an empty folder - a legit situation
        }
        return checksum.hash();
    }

    private void hashFile(Hasher combinedHash, File file) {
        combinedHash.putString(file.getAbsolutePath(), Charsets.UTF_8);
        combinedHash.putBytes(fileHasher.hash(file).asBytes());
    }
}
