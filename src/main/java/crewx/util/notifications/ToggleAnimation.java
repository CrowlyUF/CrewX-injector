package crewx.util.notifications;





public final class ToggleAnimation {
    static final long SLIDE_MS = 260L;
    static final long KNOB_DELAY_MS = 140L;
    static final long KNOB_MS = 280L;

    private ToggleAnimation() {
    }


    public static float appear(long elapsedMs, long displayMs) {
        if (elapsedMs < 0L) return 0.0F;
        if (elapsedMs >= displayMs) return 1.0F - ease((elapsedMs - displayMs) / (float) SLIDE_MS);
        return ease(elapsedMs / (float) SLIDE_MS);
    }


    public static boolean finished(long elapsedMs, long displayMs) {
        return elapsedMs >= displayMs + SLIDE_MS;
    }


    public static float knob(long elapsedMs, boolean enabled) {
        float t = ease((elapsedMs - KNOB_DELAY_MS) / (float) KNOB_MS);
        return enabled ? t : 1.0F - t;
    }

    static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (float) Math.pow(1.0F - t, 3.0D);
    }
}
