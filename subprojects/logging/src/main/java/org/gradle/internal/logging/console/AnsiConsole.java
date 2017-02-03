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

public class AnsiConsole implements Console {
    private final Flushable flushable;
    private final DefaultStatusArea statusArea;
    private final DefaultTextArea textArea;
    private final AnsiExecutor ansiExecutor;

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap) {
        this(target, flushable, colorMap, false);
    }

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap, boolean forceAnsi) {
        this(target, flushable, colorMap, new DefaultAnsiFactory(forceAnsi));
    }

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap, AnsiFactory factory) {
        this.flushable = flushable;
        this.ansiExecutor = new DefaultAnsiExecutor(target, colorMap, factory, Cursor.newBottomLeft(), new Listener());

        textArea = new DefaultTextArea(ansiExecutor);
        statusArea = new DefaultStatusArea(ansiExecutor);
    }

    @Override
    public void flush() {
        redraw();
        try {
            flushable.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void redraw() {
        // Calculate how many rows of the status area overlap with the text area
        int numberOfOverlappedRows = statusArea.getWritePosition().row - textArea.getWritePosition().row;

        // If textArea is on a status line but nothing was written, this means a new line was just written. While
        // we wait for additional text, we assume this row doesn't count as overlapping and use it as a status
        // line. In the opposite case, we want to scroll the progress area one more line. This avoid having an one
        // line gap between the text area and the status area.
        if (textArea.getWritePosition().col > 0) {
            numberOfOverlappedRows++;
        }

        statusArea.scroll(numberOfOverlappedRows);

        statusArea.redraw();
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

    private class Listener implements DefaultAnsiExecutor.NewLineListener {
        @Override
        public void beforeNewLineWritten(Cursor writeCursor) {
            if (writeCursor.row == 0) {
                textArea.newLineAdjustment();
                statusArea.newLineAdjustment();
            }

            if (statusArea.isOverlappingWith(writeCursor)) {
                ansiExecutor.writeAt(writeCursor, new Action<AnsiContext>() {
                    @Override
                    public void execute(AnsiContext ansi) {
                        ansi.eraseForward();
                    }
                });
            }
        }
    }
}
