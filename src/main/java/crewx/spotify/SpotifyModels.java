package crewx.spotify;

import java.util.ArrayList;
import java.util.List;

public final class SpotifyModels {
    private SpotifyModels() {}

    public static final class Track {
        public String id = "";
        public String uri = "";
        public String title = "";
        public String artists = "";
        public String album = "";
        public String albumUri = "";
        public String imageUrl = "";
        public int durationMs;
    }

    public static final class Playback {
        public Track track;
        public boolean playing;
        public int progressMs;
        public int volumePercent;
        public boolean shuffle;
        public boolean saved;
        public String repeat = "off";
    }

    public static final class Playlist {
        public String id = "";
        public String uri = "";
        public String name = "";
        public String owner = "";
        public String imageUrl = "";
        public int tracks;
    }

    public static final class Device {
        public String id = "";
        public String name = "";
        public String type = "";
        public boolean active;
        public boolean restricted;
    }

    public static final class TrackPage {
        public final List<Track> tracks = new ArrayList<Track>();
        public int total;
    }
}
