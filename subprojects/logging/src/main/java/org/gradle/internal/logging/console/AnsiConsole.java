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

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;

import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnsiConsole implements Console {
    private final Flushable flushable;
    private final StatusAreaImpl statusArea;
    private final TextAreaImpl textArea;

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap) {
        this(target, flushable, colorMap, false);
    }

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap, boolean forceAnsi) {
        this.flushable = flushable;

        AnsiExecutor ansiExecutor = new AnsiExecutorImpl(target, forceAnsi);
        textArea = new TextAreaImpl(ansiExecutor, colorMap);
        statusArea = new StatusAreaImpl(ansiExecutor, colorMap);
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
        AnsiExecutorImpl(Appendable target, boolean forceAnsi) {
            super(target, forceAnsi);
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

    // TODO(ew): Test this class separately (may want to extract)
    private class StatusAreaImpl implements BuildProgressArea {
        private static final int BUILD_PROGRESS_LABEL_COUNT = 4;
        private static final int STATUS_AREA_HEIGHT = 2 + BUILD_PROGRESS_LABEL_COUNT;
        private final List<RedrawableLabel> entries = new ArrayList<RedrawableLabel>(STATUS_AREA_HEIGHT);

        private final List<RedrawableLabel> buildProgressLabels = new ArrayList<RedrawableLabel>(BUILD_PROGRESS_LABEL_COUNT);
        private final Cursor statusAreaPos = new Cursor();
        private final AnsiExecutor ansiExecutor;
        private boolean isClosed;

        public StatusAreaImpl(AnsiExecutor ansiExecutor, ColorMap colorMap) {
            this.ansiExecutor = ansiExecutor;

            // TODO(ew): Way too much work being done in constructor, making this impossible to test
            this.statusAreaPos.row += STATUS_AREA_HEIGHT - 1;

            int offset = STATUS_AREA_HEIGHT - 1;

            entries.add(new DefaultRedrawableLabel(ansiExecutor, colorMap, offset--));

            for (int i = 0; i < BUILD_PROGRESS_LABEL_COUNT; ++i) {
                RedrawableLabel label = new DefaultRedrawableLabel(ansiExecutor, colorMap, offset--);
                entries.add(label);
                buildProgressLabels.add(label);
            }

            // Parking space for the write cursor
            entries.add(new DefaultRedrawableLabel(ansiExecutor, colorMap, offset--));
        }

        @Override
        public List<StyledLabel> getBuildProgressLabels() {
            List<StyledLabel> result = new ArrayList<StyledLabel>(buildProgressLabels.size());
            for (RedrawableLabel label : buildProgressLabels) {
                result.add(label);
            }
            return result;
        }

        @Override
        public StyledLabel getStatusBar() {
            return entries.get(0);
        }

        @Override
        public void close() {
            isClosed = true;

            scrollConsole(textArea.getWritePosition());

            // Clear progress area
            for (RedrawableLabel label : entries) {
                label.clear();
            }

            // Reset position
            ansiExecutor.positionCursorAt(statusAreaPos);
        }

        public boolean isOverlappingWith(Cursor cursor) {
            for (RedrawableLabel label : entries) {
                if (cursor.row == label.getWritePosition().row && label.getWritePosition().col > cursor.col) {
                    return true;
                }
            }
            return false;
        }

        public void newLineAdjustment() {
            for (RedrawableLabel label : entries) {
                label.getWritePosition().row++;
            }
        }

        public void redraw() {
            if (isClosed) {
                return;
            }

            scrollConsole(textArea.getWritePosition());

            // Redraw every entries of this area
            for (RedrawableLabel label : entries) {
                label.redraw();
            }

            parkCursor();
        }

        private void scrollConsole(Cursor upperComponentPos) {
            // Calculate how many rows of the status area overlap with the text area
            int numberOfOverlappedRows = Math.min(statusAreaPos.row - upperComponentPos.row + 1, STATUS_AREA_HEIGHT);

            // If upperComponentPos is on a status line but nothing was written, this means a new line was just written. While
            // we wait for additional text, let's assume this row doesn't count as overlapping and use it as a status
            // line. This avoid having an one line gab between the text area and the status area.
            if (upperComponentPos.col == 0) {
                numberOfOverlappedRows--;
            }

            // Scroll the console by the number of overlapping rows
            if (numberOfOverlappedRows > 0) {
                ansiExecutor.writeAt(Cursor.newBottomLeft(), newLines(numberOfOverlappedRows));
            }
        }

        private void parkCursor() {
            ansiExecutor.positionCursorAt(Cursor.newBottomLeft());
        }

        private /*static*/ Action<AnsiContext> newLines(final int numberOfNewLines) {
            return new Action<AnsiContext>() {
                @Override
                public void execute(AnsiContext ansi) {
                    for (int i = numberOfNewLines; i > 0; --i) {
                        ansi.newline();  // This ends up calling newLineWritten(...)
                    }
                }
            };
        }
    }

    private class TextAreaImpl extends AbstractTextArea {
        public TextAreaImpl(AnsiExecutor ansiExecutor, ColorMap colorMap) {
            super(ansiExecutor, colorMap);
        }

        boolean isOverlappingWith(Cursor position) {
            return statusArea.isOverlappingWith(position);
        }
    }
}
