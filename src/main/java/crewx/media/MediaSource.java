package crewx.media;


public interface MediaSource {
    interface Listener {

        void onMedia(MediaSource source, MediaInfo info);

        void onStatus(MediaSource source, String status);
    }

    String getName();

    void start(Listener listener);

    void stop();


    void control(MediaAction action, long argument);

    void refreshNow();

    String getStatus();
}
