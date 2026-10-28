package com.daniil.wristlens.shared;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import com.daniil.wristlens.core.PreviewViewport;

/** Camera frame with opt-in local pinch/pan. The drawing matrix also maps tap-to-focus. */
public final class PreviewView extends View {
    public interface Focus { void at(float x, float y); }
    private Bitmap bitmap;
    private int rotation;
    private boolean mirror, fillScreen, viewportGestures, multiplePointers, doubleTapping, ignoreTouch;
    private float previousX, previousY, previousSpan, initialSpan;
    private boolean scaling;
    private final float pinchSlop;
    private final PreviewViewport viewport = new PreviewViewport();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix(), inverse = new Matrix();
    private final GestureDetector taps;
    private final GestureDetector.SimpleOnGestureListener touchListener;
    private Focus focus;
    private String empty = Strings.tr(R.string.wl_255);
    private float tapX = -1, tapY = -1;
    private final Runnable hideFocus = () -> { tapX = -1; invalidate(); };

    public PreviewView(Context context) {
        super(context); setBackgroundColor(Color.BLACK); setContentDescription(Strings.tr(R.string.wl_256));
        // Phone-sized minimum spans can prevent pinching on a small watch. Use a local 8dp threshold.
        pinchSlop = 8 * getResources().getDisplayMetrics().density;
        touchListener = new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (!viewportGestures) focusAt(e.getX(), e.getY());
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (viewportGestures && !ignoreTouch && !multiplePointers && !doubleTapping) focusAt(e.getX(), e.getY());
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (!canAdjustViewport()) return false;
                doubleTapping = true; resetViewport(); ActionFeedback.press(PreviewView.this); return true;
            }
            @Override public boolean onDoubleTapEvent(MotionEvent e) { return viewportGestures; }
            @Override public boolean onScroll(MotionEvent first, MotionEvent e, float dx, float dy) {
                if (!canAdjustViewport() || multiplePointers || doubleTapping) return false;
                viewport.pan(-dx, -dy); clearFocusMarker(); invalidate(); return true;
            }
        };
        taps = new GestureDetector(context, touchListener);
        taps.setIsLongpressEnabled(false); taps.setOnDoubleTapListener(null);
    }
    public void fillScreen(boolean value) { fillScreen = value; updateMatrix(); invalidate(); }
    public void viewportGestures(boolean value) {
        cancelTouch(); viewportGestures = value;
        taps.setOnDoubleTapListener(value ? touchListener : null);
        viewport.reset(); updateMatrix(); invalidate();
    }
    public boolean canAdjustViewport() { return viewportGestures && bitmap != null && getWidth() > 0 && getHeight() > 0; }
    public void resetViewport() { viewport.reset(); clearFocusMarker(); updateMatrix(); invalidate(); }
    public void onFocus(Focus callback) { focus = callback; }
    public void empty(String text) { empty = text; invalidate(); }
    public void frame(Bitmap frame, int angle, boolean mirrored) {
        int normalized = ((angle % 360) + 360) % 360;
        if (viewport.ready() && (rotation != normalized || mirror != mirrored)) {
            cancelTouch(); viewport.reset(); clearFocusMarker();
        }
        bitmap = frame; rotation = normalized; mirror = mirrored;
        if (updateMatrix()) { cancelTouch(); clearFocusMarker(); }
        invalidate();
    }
    public void clear() { cancelTouch(); clearFocusMarker(); bitmap = null; invalidate(); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH); cancelTouch(); updateMatrix();
    }
    private boolean updateMatrix() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return false;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        boolean swap = rotation % 180 != 0;
        boolean changed = viewport.geometry(getWidth(), getHeight(), swap ? h : w, swap ? w : h,
                viewportGestures && getResources().getConfiguration().isScreenRound(), fillScreen);
        float scale = (float) viewport.scale();
        matrix.reset(); matrix.postTranslate(-w / 2f, -h / 2f); matrix.postRotate(rotation);
        matrix.postScale(mirror ? -scale : scale, scale);
        matrix.postTranslate(getWidth() / 2f + (float) viewport.x(), getHeight() / 2f + (float) viewport.y());
        return changed;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            paint.setColor(0xff99a4b8); paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(empty, getWidth() / 2f, getHeight() / 2f, paint); return;
        }
        updateMatrix(); canvas.drawBitmap(bitmap, matrix, paint);
        if (tapX >= 0) {
            paint.setStyle(Paint.Style.STROKE); paint.setColor(ThemeColors.accent(getContext())); paint.setStrokeWidth(2);
            canvas.drawCircle(tapX, tapY, 16 * getResources().getDisplayMetrics().density, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
    private void focusAt(float px, float py) {
        if (bitmap == null || focus == null) return;
        if (viewportGestures && getResources().getConfiguration().isScreenRound()
                && Math.hypot(px - getWidth() / 2f, py - getHeight() / 2f) > Math.min(getWidth(), getHeight()) / 2f) return;
        updateMatrix(); if (!matrix.invert(inverse)) return;
        float[] point = {px, py}; inverse.mapPoints(point);
        float x = point[0] / bitmap.getWidth(), y = point[1] / bitmap.getHeight();
        if (x < 0 || y < 0 || x > 1 || y > 1) return;
        focus.at(x, y); clearFocusMarker(); tapX = px; tapY = py;
        invalidate(); postDelayed(hideFocus, 1000); performClick();
    }
    private void clearFocusMarker() { removeCallbacks(hideFocus); tapX = -1; }
    private void cancelTap(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
        taps.onTouchEvent(cancel); cancel.recycle();
    }
    private void cancelTouch() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        if (taps != null) taps.onTouchEvent(cancel);
        cancel.recycle(); multiplePointers = doubleTapping = scaling = false; ignoreTouch = true;
        previousSpan = initialSpan = 0;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            ignoreTouch = multiplePointers = doubleTapping = scaling = false;
            if (viewportGestures && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (ignoreTouch) return true;
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiplePointers = true; doubleTapping = false; clearFocusMarker(); cancelTap(event);
        }
        if (canAdjustViewport() && multiplePointers) {
            float x = 0, y = 0; int count = 0;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (action == MotionEvent.ACTION_POINTER_UP && i == event.getActionIndex()) continue;
                x += event.getX(i); y += event.getY(i); count++;
            }
            if (count > 0) {
                x /= count; y /= count;
                double distance = 0;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (action == MotionEvent.ACTION_POINTER_UP && i == event.getActionIndex()) continue;
                    double dx = event.getX(i) - x, dy = event.getY(i) - y;
                    distance += dx * dx + dy * dy;
                }
                float span = (float) (2 * Math.sqrt(distance / count));
                if (action == MotionEvent.ACTION_MOVE) {
                    float factor = 1;
                    if (count >= 2 && span >= pinchSlop && previousSpan >= pinchSlop) {
                        if (scaling) factor = span / previousSpan;
                        else if (Math.abs(span - initialSpan) >= pinchSlop) {
                            scaling = true; factor = span / Math.max(pinchSlop, initialSpan);
                        }
                    } else { scaling = false; initialSpan = span; }
                    viewport.transform(factor, previousX, previousY, x, y);
                } else {
                    // Rebase on pointer down/up, never treating a new centroid as image movement.
                    initialSpan = span; scaling = false;
                }
                previousSpan = span;
                previousX = x; previousY = y;
            }
        }
        if (!multiplePointers) taps.onTouchEvent(event);
        if (viewportGestures && multiplePointers) { updateMatrix(); invalidate(); }
        if (action == MotionEvent.ACTION_CANCEL) cancelTouch();
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(false);
        // Keep the multi-pointer flag until the next DOWN: lifting the final finger must not focus.
        return true;
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); if (!hasFocus) { cancelTouch(); clearFocusMarker(); }
    }
    @Override protected void onDetachedFromWindow() { cancelTouch(); clearFocusMarker(); super.onDetachedFromWindow(); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
