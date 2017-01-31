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

import org.fusesource.jansi.Ansi;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.logging.text.AbstractLineChoppingStyledTextOutput;
import org.gradle.internal.logging.text.Span;
import org.gradle.internal.logging.text.Style;

import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AnsiConsole implements Console {
    private static final int CHARS_PER_TAB_STOP = 8;
    private final Appendable target;
    private final Flushable flushable;
    private final StatusAreaImpl statusArea;
    private final TextAreaImpl textArea;
    private final ColorMap colorMap;
    private final boolean forceAnsi;
    private final Cursor writeCursor = new Cursor();
    private final Cursor textCursor = new Cursor();
    // TODO(ew): Is statusAreaCursor still used? Clean up any ambiguity or tangle between Console cursors and those maintained by text area/labels
    private final Cursor statusAreaCursor = new Cursor();

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap) {
        this(target, flushable, colorMap, false);
    }

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap, boolean forceAnsi) {
        this.target = target;
        this.flushable = flushable;
        this.colorMap = colorMap;
        textArea = new TextAreaImpl(textCursor);
        statusArea = new StatusAreaImpl(statusAreaCursor);
        this.forceAnsi = forceAnsi;
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

    Ansi createAnsi() {
        if (forceAnsi) {
            return new Ansi();
        } else {
            return Ansi.ansi();
        }
    }

    private void positionCursorAt(Cursor position, Ansi ansi) {
        if (writeCursor.row == position.row) {
            if (writeCursor.col == position.col) {
                return;
            }
            if (writeCursor.col < position.col) {
                ansi.cursorRight(position.col - writeCursor.col);
            } else {
                ansi.cursorLeft(writeCursor.col - position.col);
            }
        } else {
            if (writeCursor.col > 0) {
                ansi.cursorLeft(writeCursor.col);
            }
            if (writeCursor.row < position.row) {
                ansi.cursorUp(position.row - writeCursor.row);
            } else {
                ansi.cursorDown(writeCursor.row - position.row);
            }
            if (position.col > 0) {
                ansi.cursorRight(position.col);
            }
        }
        writeCursor.copyFrom(position);
    }

    private void charactersWritten(Cursor cursor, int count) {
        writeCursor.col += count;
        cursor.copyFrom(writeCursor);
    }

    private void newLineWritten(Cursor cursor) {
        writeCursor.col = 0;

        // On any line except the bottom most one, a new line simply move the cursor to the next row.
        // Note: the next row has a lower index.
        if (writeCursor.row > 0) {
            writeCursor.row--;
        } else {
            writeCursor.row = 0;
            textCursor.row++;
            statusArea.newLineAdjustment();
        }
        cursor.copyFrom(writeCursor);
    }

    private void write(Ansi ansi) {
        try {
            target.append(ansi.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Label getStatusBar() {
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

    private static class Cursor {
        int col; // count from left of screen, 0 = left most
        int row; // count from bottom of screen, 0 = bottom most, 1 == 2nd from bottom

        public void copyFrom(Cursor position) {
            if (position == this) {
                return;
            }
            this.col = position.col;
            this.row = position.row;
        }

        public void bottomLeft() {
            col = 0;
            row = 0;
        }

        public static Cursor newBottomLeft() {
            Cursor result = new Cursor();
            result.bottomLeft();
            return result;
        }

        public static Cursor from(Cursor position) {
            Cursor result = new Cursor();
            result.copyFrom(position);
            return result;
        }
    }

    // TODO(ew): Test this class separately (may want to extract)
    private class StatusAreaImpl implements BuildProgressArea {
        private static final int BUILD_PROGRESS_LABEL_COUNT = 4;
        private static final int STATUS_AREA_HEIGHT = 2 + BUILD_PROGRESS_LABEL_COUNT;
        private final List<RedrawableLabel> entries = new ArrayList<RedrawableLabel>(STATUS_AREA_HEIGHT);

        private final List<RedrawableLabel> buildProgressLabels = new ArrayList<RedrawableLabel>(BUILD_PROGRESS_LABEL_COUNT);
        private final Cursor statusAreaPos = new Cursor();
        private boolean isClosed;

        public StatusAreaImpl(Cursor statusAreaPos) {
            // TODO(ew): Way too much work being done in constructor, making this impossible to test
            this.statusAreaPos.copyFrom(statusAreaPos);
            this.statusAreaPos.row += STATUS_AREA_HEIGHT - 1;

            int offset = STATUS_AREA_HEIGHT - 1;

            entries.add(new LabelImpl(offset--));

            for (int i = 0; i < BUILD_PROGRESS_LABEL_COUNT; ++i) {
                RedrawableLabel label = new LabelImpl(offset--);
                entries.add(label);
                buildProgressLabels.add(label);
            }

            // Parking space for the write cursor
            entries.add(new LabelImpl(offset--));

            // TODO(ew): Extract header as a separate concept
            entries.get(0).setText(Arrays.asList(new Span(Style.of(Style.Emphasis.BOLD), "<-------------> 0% INITIALIZING")));
        }

        @Override
        public List<Label> getBuildProgressLabels() {
            List<Label> result = new ArrayList<Label>(buildProgressLabels.size());
            for (RedrawableLabel label : buildProgressLabels) {
                result.add(label);
            }
            return result;
        }

        @Override
        public Label getStatusBar() {
            return entries.get(0);
        }

        @Override
        public void close() {
            isClosed = true;

            scrollConsole();

            // Clear progress area
            for (RedrawableLabel label : entries) {
                label.clear();
            }

            // Reset position
            Ansi ansi = createAnsi();
            positionCursorAt(statusAreaPos, ansi);
            write(ansi);
        }

        public boolean isOverlappingWith(Cursor cursor) {
            for (RedrawableLabel label : entries) {
                // Only look at the overlapping rows. Columns are meaningless as we don't keep track how much
                // overlapping characters was written to each rows.
                if (cursor.row == label.getWritePosition().row) {
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

            scrollConsole();

            // Redraw every entries of this area
            for (RedrawableLabel label : entries) {
                label.redraw();
            }

            parkCursor();
        }

        private void scrollConsole() {
            // Calculate how many rows of the status area overlap with the text area
            int numberOfOverlappedRows = Math.min(statusAreaPos.row - textCursor.row + 1, STATUS_AREA_HEIGHT);

            // If textCursor is on a status line but nothing was written, this means a new line was just written. While
            // we wait for additional text, let's assume this row doesn't count as overlapping and use it as a status
            // line. This avoid having an one line gab between the text area and the status area.
            if (textCursor.col == 0) {
                numberOfOverlappedRows--;
            }

            // Scroll the console by the number of overlapping rows
            if (numberOfOverlappedRows > 0) {
                Ansi ansi = createAnsi();
                Cursor scroll = Cursor.newBottomLeft();
                positionCursorAt(scroll, ansi);
                for (; numberOfOverlappedRows > 0; --numberOfOverlappedRows) {
                    ansi.newline();
                    newLineWritten(scroll);
                }
                write(ansi);
            }
        }

        private void parkCursor() {
            Ansi ansi = createAnsi();
            Cursor parking = Cursor.newBottomLeft();
            positionCursorAt(parking, ansi);
            write(ansi);
        }
    }

    private interface RedrawableLabel extends Label {
        void redraw();
        Cursor getWritePosition();
        void clear();
    }

    private abstract class AbstractRedrawableLabel implements RedrawableLabel {
        protected final Cursor writePos = new Cursor();
        protected final int offset;
        protected List<Span> spans = Collections.EMPTY_LIST;

        AbstractRedrawableLabel(int offset) {
            this.offset = offset;
        }

        public void setText(List<Span> spans) {
            this.spans = spans;
        }

        @Override
        public Cursor getWritePosition() {
            return writePos;
        }

        @Override
        public void redraw() {
            Ansi ansi = createAnsi();

            moveCursorToPosition(ansi);

            int charCount = renderLine(ansi);

            // Remove what ever may be at the end of the line
            ansi.eraseLine(Ansi.Erase.FORWARD);

            write(ansi);
            charactersWritten(writePos, charCount);
        }

        @Override
        public void clear() {
            Ansi ansi = createAnsi();

            moveCursorToPosition(ansi);

            ansi.eraseLine(Ansi.Erase.FORWARD);

            write(ansi);
        }

        private void moveCursorToPosition(Ansi ansi) {
            writePos.bottomLeft();
            writePos.row += offset;
            positionCursorAt(writePos, ansi);
        }

        abstract int renderLine(Ansi ansi);
    }

    // TODO(ew): Test this independently (may want to extract)
    private class LabelImpl extends AbstractRedrawableLabel {
        private List<Span> writtenSpans = Collections.EMPTY_LIST;

        public LabelImpl(int offset) {
            super(offset);
        }

        @Override
        public void redraw() {
            if (writePos.row == offset && writtenSpans.equals(spans)) {
                // Does not need to be redrawn
                return;
            }

            super.redraw();

            writtenSpans = spans;
        }

        @Override
        int renderLine(Ansi ansi) {
            int charCount = 0;
            for (Span span : spans) {
                ColorMap.Color color = colorMap.getColourFor(span.getStyle());
                color.on(ansi);
                ansi.a(span.getText());
                color.off(ansi);

                charCount += span.getText().length();
            }

            return charCount;
        }
    }

    private class TextAreaImpl extends AbstractLineChoppingStyledTextOutput implements TextArea {
        private final Cursor writePos;

        public TextAreaImpl(Cursor writePos) {
            this.writePos = writePos;
        }

        @Override
        protected void doLineText(CharSequence text) {
            if (text.length() == 0) {
                return;
            }
            Ansi ansi = createAnsi();
            positionCursorAt(writePos, ansi);
            ColorMap.Color color = colorMap.getColourFor(getStyle());
            color.on(ansi);

            String textStr = text.toString();
            int pos = 0;
            while (pos < text.length()) {
                int next = textStr.indexOf('\t', pos);
                if (next == pos) {
                    int charsToNextStop = CHARS_PER_TAB_STOP - (writePos.col % CHARS_PER_TAB_STOP);
                    for(int i = 0; i < charsToNextStop; i++) {
                        ansi.a(" ");
                    }
                    charactersWritten(writePos, charsToNextStop);
                    pos++;
                } else if (next > pos) {
                    ansi.a(textStr.substring(pos, next));
                    charactersWritten(writePos, next - pos);
                    pos = next;
                } else {
                    ansi.a(textStr.substring(pos, textStr.length()));
                    charactersWritten(writePos, textStr.length() - pos);
                    pos = textStr.length();
                }
            }
            color.off(ansi);
            write(ansi);
        }

        @Override
        protected void doEndLine(CharSequence endOfLine) {
            Ansi ansi = createAnsi();
            positionCursorAt(writePos, ansi);
            if (statusArea.isOverlappingWith(writePos)) {
                ansi.eraseLine(Ansi.Erase.FORWARD);
            }
            ansi.newline();
            write(ansi);
            newLineWritten(writePos);
        }
    }
}
