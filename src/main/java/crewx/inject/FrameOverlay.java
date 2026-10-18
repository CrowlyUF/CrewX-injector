package crewx.inject;

/** Allocation-free frame ownership for world-projected HUD elements. Render thread only. */
public final class FrameOverlay {
    private static long frame, worldFrame = -1, drawnFrame = -1;
    private static boolean active, drawing;
    private FrameOverlay() {}
    public static void beginFrame(boolean visibleWorld) { frame++; active=visibleWorld; drawing=false; }
    public static void worldReady() { worldFrame=frame; }
    public static boolean deferNormalDraw() { return active && !drawing; }
    public static boolean beginDraw() {
        if(!active || drawing || worldFrame!=frame || drawnFrame==frame)return false;
        drawnFrame=frame;drawing=true;return true;
    }
    public static void endDraw() { drawing=false; }
    public static void reset() { frame=0;worldFrame=drawnFrame=-1;active=drawing=false; }
}
