package com.daniil.wristlens.wear;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.daniil.wristlens.shared.Controls;
import com.daniil.wristlens.shared.PreviewView;
import java.util.LinkedHashMap;
import java.util.Map;

/** Positions controls inside the dial before FrameLayout measures its children. */
final class WatchLayout extends FrameLayout {
    private record Anchor(float x, float y, int sizeDp) {}
    private final Map<View, Anchor> anchors = new LinkedHashMap<>();
    private PreviewView preview;
    private MotionEvent firstDown;
    private boolean previewGesture;
    WatchLayout(Context context) { super(context); setMotionEventSplittingEnabled(false); }
    void preview(PreviewView view) { preview = view; }

    /** A pinch may start over a camera button. Cancel that button before taking both pointers. */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            finishGesture(); firstDown = MotionEvent.obtain(event);
        }
        if (!previewGesture && action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2
                && firstDown != null && preview != null && preview.canAdjustViewport()) {
            MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel); // Also releases a held burst or a pending long press.
            preview.onTouchEvent(cancel); // Discard a pending single tap from an earlier sequence.
            cancel.recycle(); previewGesture = true;
            preview.onTouchEvent(firstDown);
        }
        boolean handled = previewGesture ? preview.onTouchEvent(event) : super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            previewGesture = false; recycleDown();
        }
        return handled;
    }
    private void recycleDown() { if (firstDown != null) { firstDown.recycle(); firstDown = null; } }
    private void finishGesture() {
        if (previewGesture && firstDown != null && preview != null) {
            MotionEvent cancel = MotionEvent.obtain(firstDown); cancel.setAction(MotionEvent.ACTION_CANCEL);
            preview.onTouchEvent(cancel); cancel.recycle();
        }
        previewGesture = false; recycleDown();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); if (!hasFocus) finishGesture();
    }
    @Override protected void onDetachedFromWindow() { finishGesture(); super.onDetachedFromWindow(); }

    // Camera controls use fixed physical dial coordinates, independent of text direction.
    @SuppressLint("RtlHardcoded")
    void addControl(View view, float x, float y, int sizeDp) {
        anchors.put(view, new Anchor(x, y, sizeDp));
        int size = Controls.dp(getContext(), sizeDp);
        addView(view, new LayoutParams(size, size, Gravity.TOP | Gravity.LEFT));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(Controls.dp(getContext(), 240), widthSpec);
        int height = resolveSize(Controls.dp(getContext(), 240), heightSpec);
        int diameter = Math.min(width, height);
        float density = getResources().getDisplayMetrics().density;
        float scale = Math.min(1f, diameter / (208f * density));
        for (var entry : anchors.entrySet()) {
            Anchor anchor = entry.getValue();
            LayoutParams params = (LayoutParams) entry.getKey().getLayoutParams();
            int size = Math.max(1, Math.round(anchor.sizeDp * density * scale));
            params.width = params.height = size;
            params.leftMargin = Math.round(width / 2f + (anchor.x - .5f) * diameter - size / 2f);
            params.topMargin = Math.round(height / 2f + (anchor.y - .5f) * diameter - size / 2f);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
