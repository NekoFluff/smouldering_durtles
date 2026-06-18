/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smouldering_durtles.wk.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.smouldering_durtles.wk.R;
import com.smouldering_durtles.wk.util.ThemeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import static com.smouldering_durtles.wk.util.ObjectSupport.safe;

/**
 * Kanji practice sheet: animated reference box (top-left) + 7 practice boxes (2 rows × 4 cols).
 *
 * <p>Tap the reference box to pause/resume animation. Stylus/touch input in practice boxes
 * is stored in box-local coordinates and rendered per-box.</p>
 */
public final class StrokePracticeSheetView extends View {

    static final int COLS           = 4;
    static final int ROWS           = 2;
    static final int TOTAL_PRACTICE = COLS * ROWS - 1; // 7

    private static final RectF INPUT_RECT = new RectF(0, 0, 109, 109);

    // ── paints ────────────────────────────────────────────────────────────────────

    private final Paint ghostPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint userPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint refBgPaint  = new Paint();

    // ── stroke data ───────────────────────────────────────────────────────────────

    private List<String>     rawStrokeData = Collections.emptyList();
    List<KanjiStroke>        ghostStrokes  = new ArrayList<>();
    private boolean          dirty         = true;

    // ── animation ─────────────────────────────────────────────────────────────────

    private final StrokeAnimator animator = new StrokeAnimator(this::invalidate);

    // ── practice boxes ────────────────────────────────────────────────────────────

    final List<List<Path>> boxPaths  = new ArrayList<>();
    private final List<Integer> undoStack = new ArrayList<>();
    @Nullable private Path currentPath   = null;
    int currentBoxIndex = -1;

    // ── touch tracking ────────────────────────────────────────────────────────────

    private boolean refBoxTouchDown = false;

    // ── cached draw state ─────────────────────────────────────────────────────────

    private int lastBoxSize = 0;

    // ── constructors ──────────────────────────────────────────────────────────────

    public StrokePracticeSheetView(final Context context) {
        super(context); init();
    }

    public StrokePracticeSheetView(final Context context, final @Nullable AttributeSet attrs) {
        super(context, attrs); init();
    }

    public StrokePracticeSheetView(final Context context, final @Nullable AttributeSet attrs,
                                   final int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        final float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < TOTAL_PRACTICE; i++) boxPaths.add(new ArrayList<>());

        ghostPaint .setStyle(Paint.Style.STROKE);
        ghostPaint .setStrokeCap(Paint.Cap.ROUND);
        ghostPaint .setStrokeJoin(Paint.Join.ROUND);

        userPaint  .setStyle(Paint.Style.STROKE);
        userPaint  .setStrokeCap(Paint.Cap.ROUND);
        userPaint  .setStrokeJoin(Paint.Join.ROUND);

        dotPaint   .setStyle(Paint.Style.FILL);
        textPaint  .setTextAlign(Paint.Align.CENTER);
        refBgPaint .setStyle(Paint.Style.FILL);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2 * dp);

        gridPaint  .setStyle(Paint.Style.STROKE);
        gridPaint  .setStrokeWidth(1.5f * dp);
    }

    // ── sizing ────────────────────────────────────────────────────────────────────

    @Override
    protected void onMeasure(final int widthSpec, final int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        final int available = MeasureSpec.getSize(widthSpec);
        final int fallback  = (int)(100 * getResources().getDisplayMetrics().density);
        final int boxSize   = available > 0 ? available / COLS : fallback;
        setMeasuredDimension(available > 0 ? available : boxSize * COLS, boxSize * ROWS);
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int ow, final int oh) {
        if (w != ow || h != oh) { dirty = true; resetPractice(); invalidate(); }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.stop();
    }

    // ── public API ────────────────────────────────────────────────────────────────

    public void setStrokeData(final Collection<String> strokeDataList) {
        rawStrokeData = new ArrayList<>(strokeDataList);
        ghostStrokes.clear();
        animator.stop();
        resetPractice();
        dirty = true;
        invalidate();
    }

    public void clear() {
        resetPractice();
        invalidate();
    }

    /** Toggle start-dot / stroke-number hints and return the new state. */
    public boolean toggleHints() {
        animator.toggleHints();
        invalidate();
        return animator.isShowingHints();
    }

    public void setShowHints(final boolean show) {
        if (animator.isShowingHints() != show) {
            animator.toggleHints();
            invalidate();
        }
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        final int bi = undoStack.remove(undoStack.size() - 1);
        final List<Path> paths = boxPaths.get(bi);
        if (!paths.isEmpty()) paths.remove(paths.size() - 1);
        invalidate();
    }

    // ── preparation ───────────────────────────────────────────────────────────────

    void prepare() {
        if (!dirty) return;
        final int w = getWidth();
        if (w == 0) return;
        dirty = false;

        ghostStrokes.clear();
        if (rawStrokeData.isEmpty()) return;

        final int   boxSize = w / COLS;
        final Matrix matrix = new Matrix();
        matrix.setRectToRect(INPUT_RECT, new RectF(0, 0, boxSize, boxSize), Matrix.ScaleToFit.FILL);
        final float[] pt = new float[2];

        for (final String data : rawStrokeData) {
            final KanjiStroke ks = KanjiStrokeParser.parse(data);
            ks.path.transform(matrix);
            pt[0] = ks.labelX; pt[1] = ks.labelY; matrix.mapPoints(pt);
            ks.labelX = pt[0]; ks.labelY = pt[1];
            pt[0] = ks.startX; pt[1] = ks.startY; matrix.mapPoints(pt);
            ks.startX = pt[0]; ks.startY = pt[1];
            ghostStrokes.add(ks);
        }

        animator.setStrokes(ghostStrokes);
    }

    // ── touch ─────────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        safe(() -> handleTouch(event));
        return true;
    }

    private void handleTouch(final MotionEvent event) {
        final int w = getWidth();
        if (w == 0) return;
        final int boxSize = w / COLS;

        final float x   = event.getX();
        final float y   = event.getY();
        final int   col = Math.min((int)(x / boxSize), COLS - 1);
        final int   row = Math.min((int)(y / boxSize), ROWS - 1);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (row == 0 && col == 0) {
                    refBoxTouchDown = true;
                } else {
                    refBoxTouchDown = false;
                    currentBoxIndex = practiceIndex(row, col);
                    currentPath = new Path();
                    currentPath.moveTo(x - col * boxSize, y - row * boxSize);
                    animator.onDrawingStarted();
                    // Prevent any ancestor ScrollView from stealing the drawing gesture
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (currentPath != null) {
                    currentPath.lineTo(x - practiceCol(currentBoxIndex) * boxSize,
                                       y - practiceRow(currentBoxIndex) * boxSize);
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (refBoxTouchDown && row == 0 && col == 0) {
                    animator.togglePause();
                    invalidate();
                } else {
                    commitCurrentStroke(x, y, boxSize);
                    animator.onDrawingEnded();
                }
                refBoxTouchDown = false;
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                refBoxTouchDown = false;
                currentPath     = null;
                currentBoxIndex = -1;
                animator.onDrawingEnded();
                break;
            }
            default:
                break;
        }
    }

    private void commitCurrentStroke(final float x, final float y, final int boxSize) {
        if (currentPath == null || currentBoxIndex < 0) return;
        currentPath.lineTo(x - practiceCol(currentBoxIndex) * boxSize,
                           y - practiceRow(currentBoxIndex) * boxSize);
        boxPaths.get(currentBoxIndex).add(currentPath);
        undoStack.add(currentBoxIndex);
        currentPath     = null;
        currentBoxIndex = -1;
        invalidate();
    }

    private void resetPractice() {
        for (final List<Path> box : boxPaths) box.clear();
        undoStack.clear();
        currentPath     = null;
        currentBoxIndex = -1;
    }

    // ── coordinate helpers ────────────────────────────────────────────────────────

    // Layout: (row=0, col=0) = reference; everything else = practice indices 0–6.
    // Row 0: cols 1,2,3 → practice 0,1,2
    // Row 1: cols 0,1,2,3 → practice 3,4,5,6
    private static int practiceIndex(final int row, final int col) { return row == 0 ? col - 1 : 3 + col; }
    private static int practiceRow  (final int pi)                 { return pi < 3 ? 0 : 1; }
    private static int practiceCol  (final int pi)                 { return pi < 3 ? pi + 1 : pi - 3; }

    // ── drawing ───────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        safe(() -> onDrawHelper(canvas));
    }

    private void onDrawHelper(final Canvas canvas) {
        prepare();

        final int w = getWidth();
        final int h = getHeight();
        if (w == 0 || h == 0) return;

        final int boxSize = w / COLS;
        configurePaints(boxSize);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                canvas.save();
                canvas.translate(col * boxSize, row * boxSize);
                canvas.clipRect(0, 0, boxSize, boxSize);

                if (row == 0 && col == 0) {
                    canvas.drawRect(0, 0, boxSize, boxSize, refBgPaint);
                    drawBorderAndGrid(canvas, boxSize);
                    animator.draw(canvas, ghostPaint, dotPaint, textPaint);
                } else {
                    drawBorderAndGrid(canvas, boxSize);
                    drawPracticeBox(canvas, practiceIndex(row, col));
                }

                canvas.restore();
            }
        }
    }

    private void configurePaints(final int boxSize) {
        final int ghostColor = ThemeUtil.getColor(R.attr.strokeDiagramGhostColor);
        final boolean light  = ThemeUtil.isLightColor(ghostColor);
        final int borderColor     = light ? 0xFF808080 : 0xFF909090;
        final int refStrokeColor  = light ? 0xFF404040 : 0xFFD0D0D0;
        final int refBg           = light ? 0xFFF0F0F0 : 0xFF1A1A1A;

        ghostPaint .setStrokeWidth(Math.max(1f, boxSize / 70f));
        ghostPaint .setColor(refStrokeColor);
        userPaint  .setStrokeWidth(Math.max(1f, boxSize / 60f));
        userPaint  .setColor(ThemeUtil.getColor(R.attr.colorPrimary));
        dotPaint   .setColor(refStrokeColor);
        textPaint  .setColor(refStrokeColor);
        textPaint  .setTextSize(Math.max(8f, boxSize / 10f));
        borderPaint.setColor(borderColor);
        gridPaint  .setColor(borderColor);
        refBgPaint .setColor(refBg);

        if (boxSize != lastBoxSize) {
            lastBoxSize = boxSize;
            final float dash = Math.max(4f, boxSize / 14f);
            gridPaint.setPathEffect(new DashPathEffect(new float[]{dash, dash}, 0));
        }
    }

    private void drawBorderAndGrid(final Canvas canvas, final int boxSize) {
        canvas.drawRect(1f, 1f, boxSize - 1f, boxSize - 1f, borderPaint);
        canvas.drawLine(boxSize / 2f, 0,       boxSize / 2f, boxSize, gridPaint);
        canvas.drawLine(0,       boxSize / 2f, boxSize,      boxSize / 2f, gridPaint);
    }

    private void drawPracticeBox(final Canvas canvas, final int boxIndex) {
        for (final Path p : boxPaths.get(boxIndex)) canvas.drawPath(p, userPaint);
        if (currentBoxIndex == boxIndex && currentPath != null) canvas.drawPath(currentPath, userPaint);
    }
}
