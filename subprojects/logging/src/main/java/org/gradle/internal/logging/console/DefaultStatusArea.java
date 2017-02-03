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

import java.util.ArrayList;
import java.util.List;

public class DefaultStatusArea implements BuildProgressArea {
    private static final int BUILD_PROGRESS_LABEL_COUNT = 4;
    private static final int STATUS_AREA_HEIGHT = 2 + BUILD_PROGRESS_LABEL_COUNT;
    private final List<DefaultRedrawableLabel> entries = new ArrayList<DefaultRedrawableLabel>(STATUS_AREA_HEIGHT);

    private final List<RedrawableLabel> buildProgressLabels = new ArrayList<RedrawableLabel>(BUILD_PROGRESS_LABEL_COUNT);
    private final Cursor statusAreaPos = new Cursor();
    private final AnsiExecutor ansiExecutor;
    private boolean isClosed;

    public DefaultStatusArea(AnsiExecutor ansiExecutor) {
        this.ansiExecutor = ansiExecutor;

        // TODO(ew): Way too much work being done in constructor, making this impossible to test
        int offset = STATUS_AREA_HEIGHT - 1;

        entries.add(new DefaultRedrawableLabel(ansiExecutor, toCursor(offset), offset--));

        for (int i = 0; i < BUILD_PROGRESS_LABEL_COUNT; ++i) {
            DefaultRedrawableLabel label = new DefaultRedrawableLabel(ansiExecutor, toCursor(offset), offset--);
            entries.add(label);
            buildProgressLabels.add(label);
        }

        // Parking space for the write cursor
        entries.add(new DefaultRedrawableLabel(ansiExecutor, toCursor(offset), offset--));
    }

    private static Cursor toCursor(int offset) {
        Cursor result = Cursor.newBottomLeft();
        result.row = offset - (STATUS_AREA_HEIGHT - 1);
        return result;
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

    public Cursor getWritePosition() {
        return statusAreaPos;
    }

    public int getHeight() {
        return STATUS_AREA_HEIGHT;
    }

    @Override
    public void close() {
        isClosed = true;
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
        scroll(-1);
    }

    public void redraw() {
        if (isClosed) {
            // Clear progress area
            for (RedrawableLabel label : entries) {
                label.clear();
            }

            // Reset position
            ansiExecutor.positionCursorAt(statusAreaPos);
            return;
        }

        // Redraw every entries of this area
        for (RedrawableLabel label : entries) {
            label.redraw();
        }

        parkCursor();
    }

    public void scroll(int numberOfRows) {
        statusAreaPos.row -= numberOfRows;
        for (DefaultRedrawableLabel label : entries) {
            label.scroll(numberOfRows);
        }
    }

    private void parkCursor() {
        ansiExecutor.positionCursorAt(Cursor.newBottomLeft());
    }
}
