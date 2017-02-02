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
    private final Cursor writePos = new Cursor();
    private final AnsiExecutor ansiExecutor;
    private final ColorMap colorMap;
    private final int offset;
    private List<Span> spans = Collections.EMPTY_LIST;
    private List<Span> writtenSpans = Collections.EMPTY_LIST;

    DefaultRedrawableLabel(AnsiExecutor ansiExecutor, ColorMap colorMap, int offset) {
        this.ansiExecutor = ansiExecutor;
        this.colorMap = colorMap;
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

        adjustWritePosition();
        ansiExecutor.writeAt(writePos, new Action<AnsiContext>() {
            @Override
            public void execute(AnsiContext ansi) {
                for (Span span : spans) {
                    ColorMap.Color color = colorMap.getColourFor(span.getStyle());
                    ansi.withColor(color, writeText(span.getText()));
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
        adjustWritePosition();
        ansiExecutor.writeAt(writePos, new Action<AnsiContext>() {
            @Override
            public void execute(AnsiContext ansi) {
                ansi.eraseForward();
            }
        });
    }

    private void adjustWritePosition() {
        writePos.bottomLeft();
        writePos.row += offset;
    }
}
