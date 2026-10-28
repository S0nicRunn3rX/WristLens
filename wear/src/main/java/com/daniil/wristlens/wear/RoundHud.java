package com.daniil.wristlens.wear;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import com.daniil.wristlens.shared.ThemeColors;

final class RoundHud extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final Path arc = new Path();
    private final RectF bounds = new RectF();
    private String label = com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_222);
    private boolean record;
    RoundHud(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void update(String text, boolean recording) {
        label = text; record = recording; setContentDescription(text); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float radius = Math.max(1, Math.min(getWidth(), getHeight()) / 2f - 14 * density);
        bounds.set(getWidth() / 2f - radius, getHeight() / 2f - radius,
                getWidth() / 2f + radius, getHeight() / 2f + radius);
        arc.reset(); arc.addArc(bounds, 210, 120);
        paint.setShadowLayer(1.5f * density, 0, density, 0xcc000000);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(record ? 0xffff7878 : ThemeColors.accent(getContext()));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        paint.getFontMetrics(metrics);
        float fontHeight = metrics.descent - metrics.ascent;
        if (fontHeight > 17 * density) paint.setTextSize(paint.getTextSize() * 17 * density / fontHeight);
        float available = Math.max(1, (float) (2 * Math.PI * radius / 3) - 16 * density);
        float textWidth = paint.measureText(label);
        if (textWidth > available) paint.setTextSize(paint.getTextSize() * available / textWidth);
        paint.getFontMetrics(metrics);
        // CENTER already places text at the middle of the path. No extra half-arc offset.
        canvas.drawTextOnPath(label, arc, 0, -(metrics.ascent + metrics.descent) / 2, paint);
    }
}
