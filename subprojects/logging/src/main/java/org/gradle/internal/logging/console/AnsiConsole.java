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

package org.gradle.internal.logging.console;

import org.gradle.api.UncheckedIOException;

import java.io.Flushable;
import java.io.IOException;

public class AnsiConsole implements Console {
    private final Flushable flushable;
    private final StatusAreaImpl statusArea;
    private final TextAreaImpl textArea;

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap) {
        this(target, flushable, colorMap, false);
    }

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap, boolean forceAnsi) {
        this.flushable = flushable;

        AnsiExecutor ansiExecutor = new AnsiExecutorImpl(target, colorMap, forceAnsi);
        textArea = new TextAreaImpl(ansiExecutor);
        statusArea = new StatusAreaImpl(ansiExecutor);
    }

    @Override
    public void flush() {
        statusArea.redraw();
        try {
            flushable.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private class AnsiExecutorImpl extends AbstractAnsiExecutor {
        AnsiExecutorImpl(Appendable target, ColorMap colorMap, boolean forceAnsi) {
            super(target, colorMap, forceAnsi);
        }

        protected void doNewLineAdjustment() {
            textArea.newLineAdjustment();
            statusArea.newLineAdjustment();
        }
    }

    @Override
    public StyledLabel getStatusBar() {
        return statusArea.getStatusBar();
    }

    @Override
    public BuildProgressArea getBuildProgressArea() {
        return statusArea;
    }

    @Override
    public TextArea getMainArea() {
        return textArea;
    }

    private class StatusAreaImpl extends AbstractStatusArea {
        public StatusAreaImpl(AnsiExecutor ansiExecutor) {
            super(ansiExecutor);
        }

        int getNumberOfOverlappingRows() {
            // Calculate how many rows of the status area overlap with the text area
            int numberOfOverlappedRows = Math.min(getWritePosition().row - textArea.getWritePosition().row + 1, getHeight());

            // If upperComponentPos is on a status line but nothing was written, this means a new line was just written. While
            // we wait for additional text, let's assume this row doesn't count as overlapping and use it as a status
            // line. This avoid having an one line gab between the text area and the status area.
            if (textArea.getWritePosition().col == 0) {
                numberOfOverlappedRows--;
            }

            return numberOfOverlappedRows;
        }
    }

    private class TextAreaImpl extends AbstractTextArea {
        public TextAreaImpl(AnsiExecutor ansiExecutor) {
            super(ansiExecutor);
        }

        boolean isOverlappingWith(Cursor position) {
            return statusArea.isOverlappingWith(position);
        }
    }
}
