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

import java.io.Flushable;
import java.io.IOException;

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
        // FIXME: Take line wrapping into account if possible
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
        return statusArea.getEntries()[0];
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
    }

    private class StatusAreaImpl implements BuildProgressArea {
        private static final int STATUS_AREA_HEIGHT = 3;
        private final LabelImpl[] entries = new LabelImpl[STATUS_AREA_HEIGHT];

        public StatusAreaImpl(Cursor statusAreaPos) {
            for (int i = 0, offset = STATUS_AREA_HEIGHT - 1; i < STATUS_AREA_HEIGHT; ++i, --offset) {
                Cursor labelPos = new Cursor();
                labelPos.copyFrom(statusAreaPos);
                labelPos.row += offset;
                entries[i] = new LabelImpl(labelPos, offset);
            }

            entries[0].setText("1st label");
            entries[1].setText("2nd label");
            entries[2].setText("3rd label");

            Ansi ansi = createAnsi();
            positionCursorAt(Cursor.newBottomLeft(), ansi);
            for (int i = 0; i < entries.length - 1; ++i) {
                ansi.newline();

                // Don't use newLineWritten helper function as we don't want to move the status area
                textCursor.row++;
                writeCursor.row++;
            }
            write(ansi);
        }

        @Override
        public Label[] getEntries() {
            return entries;
        }

        public boolean isOverlappingWith(Cursor cursor) {
            for (LabelImpl label : entries) {
                // Only look at the overlapping rows. Columns are meaningless as we don't keep track how much
                // overlapping characters was written to each rows.
                if (cursor.row == label.writePos.row) {
                    return true;
                }
            }
            return false;
        }

        public void newLineAdjustment() {
            for (LabelImpl label : entries) {
                label.writePos.row++;
            }
        }

        public void redraw() {
            // Calculate how many rows of the status area overlap with the text area
            int numberOfOverlappedRows = Math.min(entries[0].writePos.row - textCursor.row + 1, STATUS_AREA_HEIGHT);

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

            // Redraw every entries of this area
            for (LabelImpl label : entries) {
                label.redraw();
            }
        }
    }

    // TODO: replace with BuildProgressTextArea
    // Fixed to 5 lines: 1 Build Status Line (always trimmed) and 4 Operation status lines
    // Must take into account Console dimensions in case console is very short
    private class LabelImpl implements Label {
        private final Cursor writePos;
        private final int offset;
        private String writtenText = "";
        private String text = "";

        public LabelImpl(Cursor writePos, int offset) {
            this.writePos = writePos;
            this.offset = offset;
        }

        public void setText(String text) {
            if (text.equals(this.text)) {
                return;
            }
            this.text = text;
        }

        public void redraw() {
            if (writePos.row == offset && writtenText.equals(text)) {
                // Does not need to be redrawn
                return;
            }

            Ansi ansi = createAnsi();
            writePos.bottomLeft();
            writePos.row += offset;
            positionCursorAt(writePos, ansi);
            if (text.length() > 0) {
                ColorMap.Color color = colorMap.getStatusBarColor();
                color.on(ansi);
                ansi.a(text);
                color.off(ansi);
            }

            // Remove what ever may be at the end of the line
            ansi.eraseLine(Ansi.Erase.FORWARD);

            write(ansi);
            charactersWritten(writePos, text.length());
            writtenText = text;
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
