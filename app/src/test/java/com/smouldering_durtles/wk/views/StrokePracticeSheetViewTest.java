package com.smouldering_durtles.wk.views;

import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class StrokePracticeSheetViewTest {

    private StrokePracticeSheetView sheet;

    private static final String STROKE_1 = "M10,50L100,50T1,10,50";
    private static final String STROKE_2 = "M10,80L100,80T2,10,80";

    // 400px wide → 4 cols × 100px; 2 rows → height = 200px
    private static final int W = 400;
    private static final int H = 200;

    @Before
    public void setUp() {
        sheet = new StrokePracticeSheetView(RuntimeEnvironment.getApplication());
        sheet.measure(
                View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        sheet.layout(0, 0, W, H);
    }

    // ── setStrokeData / prepare ──────────────────────────────────────────────────

    @Test
    public void setStrokeData_populatesGhostStrokesAfterLayout() {
        sheet.setStrokeData(Arrays.asList(STROKE_1, STROKE_2));
        sheet.prepare();
        assertEquals(2, sheet.ghostStrokes.size());
    }

    @Test
    public void setStrokeData_withEmptyList_clearsGhostStrokes() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        sheet.prepare();
        sheet.setStrokeData(Collections.emptyList());
        sheet.prepare();
        assertEquals(0, sheet.ghostStrokes.size());
    }

    @Test
    public void setStrokeData_beforeLayout_populatesAfterLayout() {
        final StrokePracticeSheetView fresh = new StrokePracticeSheetView(RuntimeEnvironment.getApplication());
        fresh.setStrokeData(Arrays.asList(STROKE_1));
        fresh.prepare(); // no-op: no size yet
        assertEquals(0, fresh.ghostStrokes.size());

        fresh.measure(
                View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        fresh.layout(0, 0, W, H);
        fresh.prepare();
        assertEquals(1, fresh.ghostStrokes.size());
    }

    @Test
    public void setStrokeData_clearsPracticeStrokes() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(150, 20, 180, 80); // practice box 0 (col 1, row 0)
        assertEquals(1, sheet.boxPaths.get(0).size());
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        assertEquals(0, sheet.boxPaths.get(0).size());
    }

    // ── drawing in practice boxes — row 0 ───────────────────────────────────────

    @Test
    public void draw_inBox1_addsPathToBox0() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(110, 20, 190, 80); // col=1, row=0 → practice 0
        assertEquals(1, sheet.boxPaths.get(0).size());
        assertEquals(0, sheet.boxPaths.get(1).size());
        assertEquals(0, sheet.boxPaths.get(2).size());
    }

    @Test
    public void draw_inBox2_addsPathToBox1() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(210, 20, 290, 80); // col=2, row=0 → practice 1
        assertEquals(0, sheet.boxPaths.get(0).size());
        assertEquals(1, sheet.boxPaths.get(1).size());
    }

    @Test
    public void draw_inBox3_addsPathToBox2() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(310, 20, 390, 80); // col=3, row=0 → practice 2
        assertEquals(0, sheet.boxPaths.get(0).size());
        assertEquals(0, sheet.boxPaths.get(1).size());
        assertEquals(1, sheet.boxPaths.get(2).size());
    }

    @Test
    public void draw_inReferenceBox_doesNothing() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(10, 20, 90, 80); // col=0, row=0 → reference, no drawing
        assertEquals(0, sheet.boxPaths.get(0).size());
        assertEquals(0, sheet.boxPaths.get(1).size());
        assertEquals(0, sheet.boxPaths.get(2).size());
    }

    // ── drawing in practice boxes — row 1 ───────────────────────────────────────

    @Test
    public void draw_inRow1Col0_addsPathToBox3() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(10, 120, 90, 180); // col=0, row=1 → practice 3
        assertEquals(0, sheet.boxPaths.get(0).size());
        assertEquals(0, sheet.boxPaths.get(1).size());
        assertEquals(0, sheet.boxPaths.get(2).size());
        assertEquals(1, sheet.boxPaths.get(3).size());
    }

    @Test
    public void draw_inRow1Col3_addsPathToBox6() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(310, 120, 390, 180); // col=3, row=1 → practice 6
        assertEquals(1, sheet.boxPaths.get(6).size());
        assertEquals(0, sheet.boxPaths.get(5).size());
    }

    @Test
    public void tap_addsPath() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        sheet.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 150, 50));
        sheet.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, 150, 50));
        assertEquals(1, sheet.boxPaths.get(0).size());
    }

    // ── undo ─────────────────────────────────────────────────────────────────────

    @Test
    public void undo_removesLastStroke() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(110, 20, 190, 80); // box 0
        drawStroke(210, 20, 290, 80); // box 1
        sheet.undo();
        assertEquals(1, sheet.boxPaths.get(0).size());
        assertEquals(0, sheet.boxPaths.get(1).size());
    }

    @Test
    public void undo_respectsDrawOrder_acrossBoxes() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(110, 20, 190, 80); // box 0
        drawStroke(210, 20, 290, 80); // box 1
        drawStroke(110, 30, 190, 70); // box 0 again
        sheet.undo();
        assertEquals(1, sheet.boxPaths.get(0).size());
        assertEquals(1, sheet.boxPaths.get(1).size());
    }

    @Test
    public void undo_onEmpty_doesNotThrow() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        sheet.undo(); // should not throw
        assertEquals(0, sheet.boxPaths.get(0).size());
    }

    @Test
    public void undo_acrossRows() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(110, 20, 190, 80);   // box 0 (row 0)
        drawStroke(10, 120, 90, 180);   // box 3 (row 1)
        sheet.undo();
        assertEquals(1, sheet.boxPaths.get(0).size());
        assertEquals(0, sheet.boxPaths.get(3).size());
    }

    // ── clear ────────────────────────────────────────────────────────────────────

    @Test
    public void clear_removesAllBoxStrokes() {
        sheet.setStrokeData(Arrays.asList(STROKE_1));
        drawStroke(110, 20, 190, 80);
        drawStroke(210, 20, 290, 80);
        drawStroke(310, 20, 390, 80);
        drawStroke(10, 120, 90, 180);
        sheet.clear();
        for (int i = 0; i < StrokePracticeSheetView.TOTAL_PRACTICE; i++) {
            assertEquals(0, sheet.boxPaths.get(i).size());
        }
    }

    @Test
    public void clear_keepsGhostStrokes() {
        sheet.setStrokeData(Arrays.asList(STROKE_1, STROKE_2));
        sheet.prepare();
        sheet.clear();
        assertEquals(2, sheet.ghostStrokes.size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void drawStroke(final float x0, final float y0, final float x1, final float y1) {
        sheet.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, x0, y0));
        sheet.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, (x0 + x1) / 2, (y0 + y1) / 2));
        sheet.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, x1, y1));
    }

    private static MotionEvent motionEvent(final int action, final float x, final float y) {
        return MotionEvent.obtain(0, 0, action, x, y, 0);
    }
}
