package crewx.module.modules.render;







final class MediaPlayerLayout {

    static final float MIN_WIDTH = 150.0F;

    static final float CARD_RATIO = 413.0F / 1006.0F;




    static final float LYRICS_RATIO = 0.035F + 0.25F;
    private static final float SCREEN_MARGIN = 16.0F;
    private static final float MAX_SCREEN_WIDTH_SHARE = 0.7F;


    static final long PAUSE_GRACE_MS = 4000L;

    private MediaPlayerLayout() {
    }





    static boolean shouldShow(boolean hasTrack, boolean playing, long msSinceLastPlaying, boolean chatOpen) {
        if (!hasTrack) return false;
        if (playing || chatOpen) return true;
        return msSinceLastPlaying >= 0L && msSinceLastPlaying < PAUSE_GRACE_MS;
    }






    static float width(int baseWidth, int sizePercent, float screenWidth, float screenHeight, boolean lyrics) {
        float wanted = baseWidth * (sizePercent / 100.0F);
        float maxByWidth = Math.min(screenWidth - SCREEN_MARGIN, screenWidth * MAX_SCREEN_WIDTH_SHARE);
        float heightPerWidth = CARD_RATIO + (lyrics ? LYRICS_RATIO : 0.0F);
        float maxByHeight = (screenHeight - SCREEN_MARGIN) / heightPerWidth;
        float max = Math.min(maxByWidth, maxByHeight);
        if (max < MIN_WIDTH) return max;
        return Math.max(MIN_WIDTH, Math.min(max, wanted));
    }
}
