package crewx.module.modules.misc;

import crewx.module.Module;
import crewx.spotify.GuiSpotify;
import crewx.spotify.SpotifyService;
import crewx.spotify.SpotifyWebScreen;
import net.minecraft.client.Minecraft;

/** Opens the standalone Spotify controls without running work during gameplay. */
public final class Spotify extends Module {
    private static volatile SpotifyService service;

    public Spotify() {
        super("Spotify", false);
    }

    private static SpotifyService service() {
        SpotifyService current = service;
        if (current == null) {
            synchronized (Spotify.class) {
                current = service;
                if (current == null) {
                    service = current = new SpotifyService(Minecraft.getMinecraft().mcDataDir);
                }
            }
        }
        return current;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        SpotifyService spotify = service();
        mc.displayGuiScreen(spotify.getClientId().isEmpty()
                ? new GuiSpotify(mc.currentScreen, spotify)
                : new SpotifyWebScreen(mc.currentScreen, spotify));
    }
}
