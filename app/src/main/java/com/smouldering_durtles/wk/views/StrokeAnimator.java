package com.smouldering_durtles.wk.views;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives stroke-by-stroke animation for the reference kanji box.
 *
 * <p>Animation flow: inter-stroke pause → draw stroke progressively → next stroke
 * (repeat) → linger with all strokes shown → restart. Tapping toggles pause.</p>
 *
 * <p>{@link #draw} is called from {@code onDraw} and handles its own scheduling via
 * a {@link Handler}. All callbacks run on the main thread.</p>
 */
final class StrokeAnimator {
    private static final long STROKE_MS       = 600L;
    private static final long INTER_STROKE_MS = 200L;
    private static final long LINGER_MS       = 1500L;

    private final Runnable invalidate;
    private final Handler  handler = new Handler(Looper.getMainLooper());
    private final List<PathMeasure> measures = new ArrayList<>();
    private final Path animPath = new Path();

    private List<KanjiStroke> strokes = new ArrayList<>();
    private int  finishedCount = 0;
    private long strokeStart   = -1;   // -1 = waiting for next-stroke delay
    private boolean lingering  = false;
    private boolean pending    = false; // a handler callback is already queued
    private boolean paused     = false;
    private boolean drawing    = false; // user is actively drawing in a practice box
    private boolean showHints  = true;  // show start dots and stroke numbers

    StrokeAnimator(final Runnable invalidate) {
        this.invalidate = invalidate;
    }

    // ── public API ────────────────────────────────────────────────────────────────

    /** Replace stroke set and restart the animation from stroke 1. */
    void setStrokes(final List<KanjiStroke> newStrokes) {
        handler.removeCallbacksAndMessages(null);
        strokes = new ArrayList<>(newStrokes);
        measures.clear();
        for (final KanjiStroke ks : strokes) {
            measures.add(new PathMeasure(ks.path, false));
        }
        paused  = false;
        drawing = false;
        startFromBeginning();
    }

    /** Cancel all pending callbacks (call from {@code onDetachedFromWindow}). */
    void stop() {
        handler.removeCallbacksAndMessages(null);
        pending = false;
    }

    /** Toggle between animated and fully-drawn-static modes. */
    void togglePause() {
        if (strokes.isEmpty()) return;
        paused = !paused;
        if (paused) {
            handler.removeCallbacksAndMessages(null);
            pending = false;
        } else {
            startFromBeginning();
        }
    }

    boolean isPaused() {
        return paused;
    }

    void toggleHints() {
        showHints = !showHints;
    }

    boolean isShowingHints() {
        return showHints;
    }

    /**
     * Called when the user begins a drawing gesture in a practice box.
     * Stops animation callbacks so rapid touch invalidates cannot advance the animation.
     */
    void onDrawingStarted() {
        drawing = true;
        handler.removeCallbacksAndMessages(null);
        pending = false;
    }

    /**
     * Called when the user lifts the stylus (or the gesture is cancelled).
     * Restarts the current stroke from the beginning so the animation resumes cleanly.
     */
    void onDrawingEnded() {
        drawing = false;
        if (paused || lingering || strokes.isEmpty()) return;
        // Restart the current stroke so the animation doesn't jump ahead
        strokeStart = -1;
        scheduleNextStroke();
    }

    // ── drawing ───────────────────────────────────────────────────────────────────

    /**
     * Draw the reference box contents. The canvas must already be translated and clipped
     * to the box-local coordinate system. {@code paint} must have its color and stroke
     * width set; this method temporarily adjusts alpha.
     */
    void draw(final Canvas canvas, final Paint paint,
              final Paint dotPaint, final Paint textPaint) {
        if (strokes.isEmpty()) return;

        final float dotRadius = paint.getStrokeWidth() * 1.5f;

        // Faded skeleton — shows the complete character at low opacity always
        paint.setAlpha(55);
        for (final KanjiStroke ks : strokes) {
            canvas.drawPath(ks.path, paint);
        }
        paint.setAlpha(255);

        if (paused || lingering) {
            for (final KanjiStroke ks : strokes) {
                canvas.drawPath(ks.path, paint);
            }
        } else if (drawing) {
            // Freeze: show only completed strokes without advancing or scheduling
            for (int i = 0; i < finishedCount; i++) {
                canvas.drawPath(strokes.get(i).path, paint);
            }
        } else {
            // Completed strokes
            for (int i = 0; i < finishedCount; i++) {
                canvas.drawPath(strokes.get(i).path, paint);
            }
            // Current stroke being animated
            if (strokeStart >= 0 && finishedCount < strokes.size()) {
                final float frac = Math.min(1f,
                        (float)(SystemClock.uptimeMillis() - strokeStart) / STROKE_MS);
                final PathMeasure pm = measures.get(finishedCount);
                animPath.reset();
                pm.getSegment(0, pm.getLength() * frac, animPath, true);
                canvas.drawPath(animPath, paint);

                if (frac >= 1f) {
                    finishedCount++;
                    handler.removeCallbacksAndMessages(null);
                    pending = false;
                    if (finishedCount >= strokes.size()) {
                        lingering = true;
                        handler.postDelayed(this::restartAfterLinger, LINGER_MS);
                    } else {
                        scheduleNextStroke();
                    }
                } else if (!pending) {
                    pending = true;
                    handler.postDelayed(() -> { pending = false; invalidate.run(); }, 16);
                }
            }
        }

        if (showHints) {
            for (final KanjiStroke ks : strokes) {
                canvas.drawCircle(ks.startX, ks.startY, dotRadius, dotPaint);
            }
            for (final KanjiStroke ks : strokes) {
                if (ks.number > 0) {
                    canvas.drawText(Integer.toString(ks.number), ks.labelX, ks.labelY, textPaint);
                }
            }
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────────

    private void startFromBeginning() {
        handler.removeCallbacksAndMessages(null);
        finishedCount = 0;
        lingering     = false;
        strokeStart   = -1;
        scheduleNextStroke();
    }

    private void scheduleNextStroke() {
        pending = true;
        handler.postDelayed(() -> {
            pending      = false;
            strokeStart  = SystemClock.uptimeMillis();
            invalidate.run();
        }, INTER_STROKE_MS);
    }

    private void restartAfterLinger() {
        lingering     = false;
        finishedCount = 0;
        strokeStart   = -1;
        scheduleNextStroke();
    }
}
