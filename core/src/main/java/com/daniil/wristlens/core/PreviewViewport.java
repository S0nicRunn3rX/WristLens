package com.daniil.wristlens.core;

/** Local display geometry only; never changes the phone camera zoom or saved media. */
public final class PreviewViewport {
    public static final double MAX_ZOOM = 3;
    private double width, height, frameWidth, frameHeight, baseScale;
    private double minimumZoom = 1, zoom = 1, offsetX, offsetY;
    private boolean round, fill, ready;

    /** Returns true when a new aspect/orientation policy requires a centered reset. */
    public boolean geometry(double width, double height, double frameWidth, double frameHeight,
                            boolean round, boolean fill) {
        if (!positive(width) || !positive(height) || !positive(frameWidth) || !positive(frameHeight)) return false;
        boolean changed = ready && (Math.abs(frameWidth / frameHeight - this.frameWidth / this.frameHeight) > .002
                || round != this.round || fill != this.fill);
        boolean fitted = ready && Math.abs(zoom - minimumZoom) < .00001;
        this.width = width; this.height = height; this.frameWidth = frameWidth; this.frameHeight = frameHeight;
        this.round = round; this.fill = fill;
        baseScale = fill ? Math.max(width / frameWidth, height / frameHeight)
                : Math.min(width / frameWidth, height / frameHeight);
        // Fitting a rectangle's width into a circle still clips its corners. Fit its diagonal instead.
        double contain = round ? Math.min(width, height) / Math.hypot(frameWidth, frameHeight)
                : Math.min(width / frameWidth, height / frameHeight);
        minimumZoom = Math.min(1, contain / baseScale);
        ready = true;
        if (changed) reset();
        else { zoom = fitted ? minimumZoom : clamp(zoom, minimumZoom, MAX_ZOOM); constrain(); }
        return changed;
    }
    public boolean ready() { return ready; }
    public double zoom() { return zoom; }
    public double minimumZoom() { return minimumZoom; }
    public double scale() { return baseScale * zoom; }
    public double x() { return offsetX * width; }
    public double y() { return offsetY * height; }
    public double displayedWidth() { return frameWidth * scale(); }
    public double displayedHeight() { return frameHeight * scale(); }
    public void reset() { zoom = 1; offsetX = offsetY = 0; }

    public void pan(double dx, double dy) {
        if (!ready || !Double.isFinite(dx) || !Double.isFinite(dy)) return;
        offsetX += dx / width; offsetY += dy / height;
        constrain();
    }
    /** Keep the image point under the pinch centroid stationary, subject to edge constraints. */
    public void scaleBy(double factor, double focusX, double focusY) {
        transform(factor, focusX, focusY, focusX, focusY);
    }
    /** Apply simultaneous scale and movement before clamping, so edges do not cause a jump. */
    public void transform(double factor, double fromX, double fromY, double toX, double toY) {
        if (!ready || !positive(factor) || !Double.isFinite(fromX) || !Double.isFinite(fromY)
                || !Double.isFinite(toX) || !Double.isFinite(toY)) return;
        double next = clamp(zoom * factor, minimumZoom, MAX_ZOOM), ratio = next / zoom;
        offsetX = (toX - width / 2 - ratio * (fromX - width / 2 - x())) / width;
        offsetY = (toY - height / 2 - ratio * (fromY - height / 2 - y())) / height;
        zoom = next; constrain();
    }
    private void constrain() {
        double maxX = Math.max(0, (displayedWidth() - width) / 2) / width;
        double maxY = Math.max(0, (displayedHeight() - height) / 2) / height;
        offsetX = clamp(offsetX, -maxX, maxX); offsetY = clamp(offsetY, -maxY, maxY);
    }
    private static boolean positive(double n) { return Double.isFinite(n) && n > 0; }
    private static double clamp(double n, double lo, double hi) { return Math.max(lo, Math.min(hi, n)); }
}
