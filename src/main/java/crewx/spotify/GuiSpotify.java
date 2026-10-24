package crewx.spotify;

import crewx.CrewX;
import crewx.module.Module;
import crewx.spotify.SpotifyModels.*;
import crewx.script.RemoteImageCache;
import crewx.inject.CrewXRuntimeHooks;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.gui.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

public final class GuiSpotify extends GuiScreen {
    private enum View { HOME, PLAYLISTS, TRACKS, SEARCH }
    private final GuiScreen parent;
    private final SpotifyService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "CrewX Spotify API"); t.setDaemon(true); return t; });
    private volatile Playback playback;
    private volatile List<Playlist> playlists = Collections.emptyList();
    private volatile List<Track> tracks = Collections.emptyList();
    private volatile String status = "Pronto";
    private volatile boolean busy;
    private View view = View.HOME;
    private GuiTextField clientId;
    private GuiTextField search;
    private String selectedContextUri;
    private String selectedPlaylistName = "Sua biblioteca";
    private int page;
    private final long openedAt = System.currentTimeMillis();

    public GuiSpotify(GuiScreen parent, SpotifyService service) { this.parent = parent; this.service = service; }

    @Override public void initGui() {
        buttonList.clear();
        clientId = new GuiTextField(1, fontRendererObj, width / 2 - 150, 64, 300, 20);
        clientId.setMaxStringLength(128); clientId.setText(service.getClientId());
        search = new GuiTextField(2, fontRendererObj, width / 2 - 145, 43, 240, 20); search.setMaxStringLength(120);
        rebuildButtons();
        if (service.isConfigured()) mc.addScheduledTask(() -> {
            if (mc.currentScreen == this) mc.displayGuiScreen(new SpotifyWebScreen(parent, service));
        });
    }

    private void rebuildButtons() {
        buttonList.clear();
        buttonList.add(new SpotifyButton(0, 5, height - 25, 70, 20, "Voltar"));
        if (!service.isConfigured()) {
            buttonList.add(new SpotifyButton(1, width / 2 - 150, 90, 145, 20, "Salvar Client ID"));
            buttonList.add(new SpotifyButton(2, width / 2 + 5, 90, 145, 20, "Conectar"));
            return;
        }
        buttonList.add(new SpotifyButton(3, width - 85, 5, 80, 20, "Desconectar"));
        if (view == View.HOME) homeButtons();
        else if (view == View.PLAYLISTS) listButtons(true);
        else if (view == View.TRACKS) listButtons(false);
        else searchButtons();
    }

    private void homeButtons() {
        int y = 104, x = width / 2 - 157;
        buttonList.add(new SpotifyButton(10, x, y, 50, 20, "Anterior"));
        buttonList.add(new SpotifyButton(11, x + 54, y, 50, 20, playback != null && playback.playing ? "Pause" : "Play"));
        buttonList.add(new SpotifyButton(12, x + 108, y, 50, 20, "Proxima"));
        buttonList.add(new SpotifyButton(13, x + 162, y, 76, 20, "Shuffle"));
        buttonList.add(new SpotifyButton(14, x + 242, y, 72, 20, "Repeat"));
        buttonList.add(new SpotifyButton(15, x, y + 25, 70, 20, "Volume -"));
        buttonList.add(new SpotifyButton(16, x + 74, y + 25, 70, 20, "Volume +"));
        buttonList.add(new SpotifyButton(17, x + 148, y + 25, 80, 20, "Playlists"));
        buttonList.add(new SpotifyButton(18, x + 232, y + 25, 82, 20, "Curtidas"));
        buttonList.add(new SpotifyButton(19, x, y + 50, 314, 20, "Pesquisar musicas"));
        buttonList.add(new SpotifyButton(21, x, y + 75, 154, 20, playback != null && playback.saved ? "Descurtir musica" : "Curtir musica"));
        buttonList.add(new SpotifyButton(20, x + 160, y + 75, 154, 20, "Atualizar"));
    }

    private void listButtons(boolean playlistMode) {
        int side = Math.max(145, Math.min(220, width / 4));
        int mainX = side + 14;
        buttonList.add(new SpotifyButton(30, mainX, 31, 66, 20, "Inicio"));
        if (selectedContextUri != null) buttonList.add(new SpotifyButton(31, mainX + 70, 31, 125, 20, "▶ Tocar playlist"));
        buttonList.add(new SpotifyButton(32, width - 105, 31, 95, 20, "Pesquisar"));
        int rows = Math.max(3, (height - 125) / 22);
        for (int i = 0; i < Math.min(rows, playlists.size()); i++) {
            Playlist p = playlists.get(i);
            buttonList.add(new SpotifyButton(1000 + i, 10, 58 + i * 22, side - 16, 20, trim(p.name, 25)));
        }
        if (playlistMode) return;
        int start = page * rows;
        for (int i = start; i < Math.min(start + rows, tracks.size()); i++) {
            Track t = tracks.get(i);
            String label = (i + 1) + "   " + t.title + "  •  " + t.artists + (t.album.isEmpty() ? "" : "  —  " + t.album);
            buttonList.add(new SpotifyButton(100 + i, mainX, 72 + (i - start) * 22, width - mainX - 10, 20, trim(label, Math.max(35, width / 7))));
        }
        if (page > 0) buttonList.add(new SpotifyButton(40, mainX, height - 68, 80, 20, "Anterior"));
        if (start + rows < tracks.size()) buttonList.add(new SpotifyButton(41, mainX + 85, height - 68, 80, 20, "Proxima"));
    }

    private void searchButtons() {
        buttonList.add(new SpotifyButton(30, width / 2 - 190, 43, 40, 20, "Inicio"));
        buttonList.add(new SpotifyButton(50, width / 2 + 100, 43, 90, 20, "Pesquisar"));
        int rows = pageSize(70);
        int start = page * rows;
        for (int i = start; i < Math.min(start + rows, tracks.size()); i++) {
            Track t = tracks.get(i);
            buttonList.add(new SpotifyButton(100 + i, width / 2 - 190, 70 + (i - start) * 21, 380, 20, trim(t.title + " - " + t.artists, 58)));
        }
        if (page > 0) buttonList.add(new SpotifyButton(40, width / 2 - 90, height - 25, 80, 20, "Anterior"));
        if (start + rows < tracks.size()) buttonList.add(new SpotifyButton(41, width / 2 + 10, height - 25, 80, 20, "Proxima"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) { mc.displayGuiScreen(parent); return; }
        if (button.id == 1) { service.setClientId(clientId.getText()); status = "Client ID salvo"; return; }
        if (button.id == 2) {
            service.setClientId(clientId.getText()); status = "Aguardando autorizacao no navegador...";
            SpotifyAuth.connect(service, error -> mc.addScheduledTask(() -> {
                if (mc.currentScreen != this) return;
                if (error == null) mc.displayGuiScreen(new SpotifyWebScreen(parent, service));
                else { status = "Erro: " + error; rebuildButtons(); }
            })); return;
        }
        if (button.id == 3) { service.disconnect(); playback = null; status = "Desconectado"; rebuildButtons(); return; }
        if (button.id == 30) { view = View.HOME; page = 0; rebuildButtons(); return; }
        if (button.id == 32) { view = View.SEARCH; tracks = Collections.emptyList(); page = 0; rebuildButtons(); return; }
        if (button.id == 40) { page--; rebuildButtons(); return; }
        if (button.id == 41) { page++; rebuildButtons(); return; }
        if (button.id == 50) { run("Pesquisando...", () -> { tracks = service.search(search.getText()).tracks; status = tracks.size() + " resultados"; }); return; }
        if (button.id >= 1000) {
            int index = button.id - 1000;
            if (index < playlists.size()) {
                Playlist p = playlists.get(index); selectedContextUri = p.uri; selectedPlaylistName = p.name; page = 0;
                run("Carregando " + p.name + "...", () -> { tracks = service.playlistTracks(p.id).tracks; view = View.TRACKS; status = p.name; });
            }
            return;
        }
        if (button.id >= 100) {
            int index = button.id - 100;
            if (view == View.PLAYLISTS && index < playlists.size()) {
                Playlist p = playlists.get(index); selectedContextUri = p.uri; selectedPlaylistName = p.name; page = 0;
                run("Carregando " + p.name + "...", () -> { tracks = service.playlistTracks(p.id).tracks; view = View.TRACKS; status = p.name; });
            } else if (index < tracks.size()) {
                Track t = tracks.get(index); run("Tocando " + t.title + "...", () -> { service.playTrack(view == View.SEARCH || selectedContextUri == null ? t.albumUri : selectedContextUri, t.uri); refreshPlaybackSync(); });
            }
            return;
        }
        switch (button.id) {
            case 10: run("Voltando...", () -> { service.previous(); refreshPlaybackSync(); }); break;
            case 11: run("Atualizando...", () -> { if (playback != null && playback.playing) service.pause(); else service.play(); refreshPlaybackSync(); }); break;
            case 12: run("Avancando...", () -> { service.next(); refreshPlaybackSync(); }); break;
            case 13: run("Alterando shuffle...", () -> { service.shuffle(playback == null || !playback.shuffle); refreshPlaybackSync(); }); break;
            case 14: run("Alterando repeat...", () -> { String r = playback == null ? "context" : "off".equals(playback.repeat) ? "context" : "context".equals(playback.repeat) ? "track" : "off"; service.repeat(r); refreshPlaybackSync(); }); break;
            case 15: run("Ajustando volume...", () -> { service.volume((playback == null ? 50 : playback.volumePercent) - 10); refreshPlaybackSync(); }); break;
            case 16: run("Ajustando volume...", () -> { service.volume((playback == null ? 50 : playback.volumePercent) + 10); refreshPlaybackSync(); }); break;
            case 17: page = 0; selectedContextUri = null; run("Carregando playlists...", () -> { playlists = service.playlists(); view = View.PLAYLISTS; status = playlists.size() + " playlists"; }); break;
            case 18: page = 0; selectedContextUri = null; run("Carregando curtidas...", () -> { tracks = service.savedTracks().tracks; view = View.TRACKS; status = tracks.size() + " musicas curtidas"; }); break;
            case 19: view = View.SEARCH; tracks = Collections.emptyList(); page = 0; rebuildButtons(); break;
            case 20: refreshPlayback(); break;
            case 21: if (playback != null && playback.track != null) run("Atualizando curtida...", () -> { service.saveTrack(playback.track.id, !playback.saved); refreshPlaybackSync(); }); break;
            case 31: if (selectedContextUri != null) run("Iniciando playlist...", () -> { service.playPlaylist(selectedContextUri); refreshPlaybackSync(); }); break;
        }
    }

    private interface Work { void run() throws Exception; }
    private void run(String message, Work work) {
        if (busy) return; busy = true; status = message;
        executor.submit(() -> { try { work.run(); } catch (Throwable e) { status = friendly(e); } finally { busy = false; mc.addScheduledTask(this::rebuildButtons); } });
    }
    private void refreshPlayback() { run("Atualizando...", this::refreshPlaybackSync); }
    private void refreshPlaybackSync() throws Exception {
        playback = service.playback();
        if (playback != null && playback.track != null && !playback.track.imageUrl.isEmpty()) RemoteImageCache.fetch("spotify_now", playback.track.imageUrl);
        status = playback == null ? "Nenhuma reproducao ativa" : "Conectado";
    }
    private static String friendly(Throwable e) { String m = e.getMessage(); return "Erro: " + (m == null ? e.getClass().getSimpleName() : trim(m, 90)); }
    private static String trim(String s, int max) { return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 3) + "..."; }
    private int pageSize(int top) { return Math.max(3, (height - top - 58) / 21); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF080808);
        drawRect(5, 5, width - 5, height - 48, 0xFF121212);
        drawRect(5, height - 43, width - 5, height - 5, 0xFF050505);
        drawRect(5, 5, width - 5, 27, 0xFF181818);
        drawString(fontRendererObj, "▶", 13, 12, 0xFF1ED760);
        drawString(fontRendererObj, "CrewX Spotify", 28, 12, 0xFFFFFFFF);
        if ((view == View.PLAYLISTS || view == View.TRACKS) && service.isConfigured()) {
            int side = Math.max(145, Math.min(220, width / 4));
            drawRect(6, 28, side + 4, height - 49, 0xFF101010);
            drawRect(side + 5, 28, side + 7, height - 49, 0xFF050505);
            drawString(fontRendererObj, "SUA BIBLIOTECA", 13, 38, 0xFFB3B3B3);
            drawString(fontRendererObj, selectedPlaylistName, side + 16, 58, 0xFFFFFFFF);
            if (view == View.TRACKS) drawString(fontRendererObj, "#    TITULO / ARTISTA / ALBUM", side + 16, 60, 0xFF888888);
        }
        if (!service.isConfigured()) {
            drawCenteredString(fontRendererObj, "Cole o Client ID criado no Spotify Developer Dashboard", width / 2, 43, 0xFFFFFFFF);
            clientId.drawTextBox();
            drawCenteredString(fontRendererObj, "Redirect URI: http://127.0.0.1:43791/spotify-oauth-callback", width / 2, 120, 0xFFAAAAAA);
        } else if (view == View.HOME) {
            Playback p = playback;
            String title = p == null || p.track == null ? "Nenhuma musica" : p.track.title;
            String artist = p == null || p.track == null ? "" : p.track.artists;
            drawCenteredString(fontRendererObj, trim(title, 60), width / 2, 43, 0xFFFFFFFF);
            drawCenteredString(fontRendererObj, trim(artist, 60), width / 2, 56, 0xFFBBBBBB);
            if (p != null) {
                String info = time(p.progressMs) + " / " + time(p.track == null ? 0 : p.track.durationMs) + "   Volume " + p.volumePercent + "%   Shuffle " + (p.shuffle ? "ON" : "OFF") + "   Repeat " + p.repeat;
                drawCenteredString(fontRendererObj, info, width / 2, 76, 0xFFAAAAAA);
                if (p.track != null && p.track.durationMs > 0) {
                    int left = width / 2 - 155, right = width / 2 + 155, y = height - 25;
                    drawRect(left, y, right, y + 3, 0xFF4A4A4A);
                    int progress = left + (int)((right - left) * Math.max(0.0, Math.min(1.0, (double)p.progressMs / p.track.durationMs)));
                    drawRect(left, y, progress, y + 3, 0xFF1ED760);
                }
            }
        } else if (view == View.SEARCH) search.drawTextBox();
        Playback bar = playback;
        if (bar != null && bar.track != null) {
            ResourceLocation cover = RemoteImageCache.texture("spotify_now");
            if (cover != null) {
                mc.getTextureManager().bindTexture(cover);
                Gui.drawScaledCustomSizeModalRect(10, height - 39, 0, 0, 64, 64, 32, 32, 64, 64);
            }
            drawString(fontRendererObj, trim(bar.track.title, 32), 48, height - 35, 0xFFFFFFFF);
            drawString(fontRendererObj, trim(bar.track.artists, 32), 48, height - 22, 0xFF999999);
        }
        drawString(fontRendererObj, trim(status, 80), Math.max(width / 2, width - 360), height - 16, busy ? 0xFFFFFF55 : 0xFF888888);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String time(int ms) { int sec = Math.max(0, ms / 1000); return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60); }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || isCloseBind(keyCode)) {
            if (keyCode != 1) CrewXRuntimeHooks.consumeBindPress(keyCode);
            mc.displayGuiScreen(parent);
            return;
        }
        if (!service.isConfigured()) clientId.textboxKeyTyped(typedChar, keyCode);
        if (view == View.SEARCH) search.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }
    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (isCloseBind(mouseButton - 100)) {
            CrewXRuntimeHooks.consumeBindPress(mouseButton - 100);
            mc.displayGuiScreen(parent);
            return;
        }
        if (!service.isConfigured()) clientId.mouseClicked(mouseX, mouseY, mouseButton);
        if (view == View.SEARCH) search.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    @Override public void onGuiClosed() { executor.shutdownNow(); }
    private boolean isCloseBind(int key) {
        if (System.currentTimeMillis() - openedAt < 300L || CrewX.moduleManager == null) return false;
        Module module = CrewX.moduleManager.getModule("Spotify");
        return module != null && module.getKey() != 0 && module.getKey() == key;
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
