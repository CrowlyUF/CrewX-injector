package crewx.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;









public final class LyricsTimeline {

    static final long PLAIN_BASE_MS = 1800L;
    static final long PLAIN_PER_CHAR_MS = 90L;

    static final long HOLD_AFTER_LINE_MS = 6000L;

    private final List<LyricLine> lines;
    private final boolean estimated;

    private LyricsTimeline(List<LyricLine> lines, boolean estimated) {
        this.lines = lines;
        this.estimated = estimated;
    }


    public static LyricsTimeline of(Lyrics lyrics, long durationMs) {
        if (lyrics == null || lyrics.state != Lyrics.State.FOUND || lyrics.lines.isEmpty()) {
            return new LyricsTimeline(Collections.<LyricLine>emptyList(), false);
        }
        if (lyrics.synced) return new LyricsTimeline(collapse(lyrics.lines, false), false);
        return new LyricsTimeline(estimate(collapse(lyrics.lines, true), durationMs), true);
    }


    public boolean sameText(int a, int b) {
        if (a < 0 || b < 0 || a >= this.lines.size() || b >= this.lines.size()) return false;
        return this.lines.get(a).text.trim().replaceAll("\\s+", " ")
                .equalsIgnoreCase(this.lines.get(b).text.trim().replaceAll("\\s+", " "));
    }

    public List<LyricLine> lines() {
        return this.lines;
    }


    public boolean isEstimated() {
        return this.estimated;
    }





    public int indexAt(long positionMs) {
        int active = -1;
        for (int i = 0; i < this.lines.size(); i++) {
            if (this.lines.get(i).timeMs <= positionMs) active = i;
            else break;
        }
        if (active < 0 || this.estimated) return active;
        LyricLine line = this.lines.get(active);
        long nextAt = active + 1 < this.lines.size() ? this.lines.get(active + 1).timeMs : Long.MAX_VALUE;
        long hideAt = line.timeMs + sweepMs(line) + HOLD_AFTER_LINE_MS;
        return positionMs >= hideAt && hideAt < nextAt ? -1 : active;
    }


    public float progress(int index, long positionMs, long durationMs) {
        if (index < 0 || index >= this.lines.size()) return 0.0F;
        if (this.estimated) return 1.0F;
        LyricLine line = this.lines.get(index);
        long end;
        if (index + 1 < this.lines.size()) end = this.lines.get(index + 1).timeMs;
        else end = durationMs > line.timeMs ? durationMs : line.timeMs + 4000L;
        float gap = Math.max(300L, end - line.timeMs);
        float sweep = Math.max(250.0F, Math.min(gap * 0.92F, sweepMs(line)));
        return Math.max(0.0F, Math.min(1.0F, (positionMs - line.timeMs) / sweep));
    }

    private static long sweepMs(LyricLine line) {
        return Math.max(1, line.text.length()) * 150L + 400L;
    }


    static List<LyricLine> collapse(List<LyricLine> source, boolean mergeRepeats) {
        List<LyricLine> result = new ArrayList<>();
        String previous = null;
        for (LyricLine line : source) {
            String text = line.text == null ? "" : line.text.trim();
            if (text.isEmpty()) continue;
            String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (mergeRepeats && normalized.equals(previous)) continue;
            previous = normalized;
            result.add(new LyricLine(line.timeMs, text));
        }
        return result;
    }


    static List<LyricLine> estimate(List<LyricLine> plain, long durationMs) {
        List<LyricLine> result = new ArrayList<>(plain.size());
        if (plain.isEmpty()) return result;
        long[] weights = new long[plain.size()];
        long total = 0L;
        for (int i = 0; i < plain.size(); i++) {
            weights[i] = PLAIN_BASE_MS + plain.get(i).text.length() * PLAIN_PER_CHAR_MS;
            total += weights[i];
        }
        long start = 0L;
        double scale = 1.0D;
        if (durationMs > 0L) {
            start = Math.round(durationMs * 0.08D);
            long span = Math.round(durationMs * 0.84D);
            scale = span / (double) total;
        }
        double at = start;
        for (int i = 0; i < plain.size(); i++) {
            result.add(new LyricLine(Math.round(at), plain.get(i).text));
            at += weights[i] * scale;
        }
        return result;
    }
}
