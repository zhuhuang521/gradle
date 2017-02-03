/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.internal.logging.text.Span;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultRedrawableLabel implements RedrawableLabel {
    private final Cursor writePos;
    private final AnsiExecutor ansiExecutor;
    private final int offset;
    private List<Span> spans = Collections.EMPTY_LIST;
    private List<Span> writtenSpans = Collections.EMPTY_LIST;

    DefaultRedrawableLabel(AnsiExecutor ansiExecutor, Cursor writePos, int offset) {
        this.ansiExecutor = ansiExecutor;
        this.writePos = writePos;
        this.offset = offset;
    }

    @Override
    public void setText(String text) {
        setText(new Span(text));
    }

    @Override
    public void setText(List<Span> spans) {
        this.spans = spans;
    }

    @Override
    public void setText(Span... spans) {
        setText(Arrays.asList(spans));
    }

    @Override
    public Cursor getWritePosition() {
        return writePos;
    }

    @Override
    public void redraw() {
        if (writePos.row == offset && writtenSpans.equals(spans)) {
            // Does not need to be redrawn
            return;
        }


        int newLines = 0 - writePos.row;
        if (newLines > 0) {
            ansiExecutor.writeAt(Cursor.newBottomLeft(), newLines(newLines));
        }

        writePos.col = 0;
        if (writePos.row > offset) {
            writePos.row = offset;
        }
        ansiExecutor.writeAt(writePos, new Action<AnsiContext>() {
            @Override
            public void execute(AnsiContext ansi) {
                for (Span span : spans) {
                    ansi.withStyle(span.getStyle(), writeText(span.getText()));
                }

                // Remove what ever may be at the end of the line
                ansi.eraseForward();
            }
        });

        writtenSpans = spans;
    }

    private static Action<AnsiContext> writeText(final String text) {
        return new Action<AnsiContext>() {
            @Override
            public void execute(AnsiContext ansi) {
                ansi.a(text);
            }
        };
    }

    @Override
    public void clear() {
        if (writePos.row >= 0) {
            writePos.col = 0;
            ansiExecutor.writeAt(writePos, new Action<AnsiContext>() {
                @Override
                public void execute(AnsiContext ansi) {
                    ansi.eraseForward();
                }
            });
        }
    }

    public void newLineAdjustment() {
        scroll(-1);
    }

    public void scroll(int numberOfRows) {
        writePos.row -= numberOfRows;
    }

    private static Action<AnsiContext> newLines(final int numberOfNewLines) {
        return new Action<AnsiContext>() {
            @Override
            public void execute(AnsiContext ansi) {
                for (int i = numberOfNewLines; i > 0; --i) {
                    ansi.newline();
                }
            }
        };
    }
}
