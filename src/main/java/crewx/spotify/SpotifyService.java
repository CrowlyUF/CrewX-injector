package crewx.spotify;

import com.google.gson.*;
import crewx.spotify.SpotifyModels.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class SpotifyService {
    private static final String API = "https://api.spotify.com/v1";
    private static final String TOKEN = "https://accounts.spotify.com/api/token";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file;
    private volatile String clientId = "";
    private volatile String refreshToken = "";
    private volatile String accessToken = "";
    private volatile long expiresAt;

    public SpotifyService(File dataDirectory) {
        File dir = new File(dataDirectory, "CrewX");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "spotify.json");
        load();
    }

    public synchronized String getClientId() { return clientId; }
    public synchronized boolean isConfigured() { return !clientId.isEmpty() && !refreshToken.isEmpty(); }

    public synchronized void setClientId(String value) {
        clientId = value == null ? "" : value.trim();
        save();
    }

    public synchronized void setRefreshToken(String value) {
        refreshToken = value == null ? "" : value.trim();
        accessToken = "";
        expiresAt = 0L;
        save();
    }

    public synchronized void disconnect() {
        refreshToken = "";
        accessToken = "";
        expiresAt = 0L;
        save();
    }

    private void load() {
        if (!file.isFile()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            clientId = string(json, "clientId");
            refreshToken = string(json, "refreshToken");
        } catch (Throwable ignored) {}
    }

    private synchronized void save() {
        JsonObject json = new JsonObject();
        json.addProperty("clientId", clientId);
        json.addProperty("refreshToken", refreshToken);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException ignored) {}
    }

    public synchronized String accessToken() throws IOException {
        if (!accessToken.isEmpty() && System.currentTimeMillis() < expiresAt - 30000L) return accessToken;
        if (!isConfigured()) throw new IOException("Spotify ainda nao foi conectado");
        JsonObject json = requestToken("grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId));
        accessToken = string(json, "access_token");
        if (json.has("refresh_token")) refreshToken = string(json, "refresh_token");
        int seconds = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
        expiresAt = System.currentTimeMillis() + seconds * 1000L;
        save();
        return accessToken;
    }

    public synchronized void exchangeCode(String code, String verifier, String redirectUri) throws IOException {
        String body = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(redirectUri)
                + "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier);
        JsonObject json = requestToken(body);
        accessToken = string(json, "access_token");
        refreshToken = string(json, "refresh_token");
        int seconds = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
        expiresAt = System.currentTimeMillis() + seconds * 1000L;
        save();
    }

    private JsonObject requestToken(String body) throws IOException {
        HttpURLConnection connection = open(TOKEN, "POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        write(connection, body);
        return object(read(connection));
    }

    public Playback playback() throws IOException {
        String body = api("GET", "/me/player", null);
        if (body.isEmpty()) return null;
        JsonObject json = object(body);
        Playback out = new Playback();
        out.playing = bool(json, "is_playing");
        out.progressMs = integer(json, "progress_ms");
        out.shuffle = bool(json, "shuffle_state");
        out.repeat = string(json, "repeat_state");
        if (json.has("device") && json.get("device").isJsonObject()) out.volumePercent = integer(json.getAsJsonObject("device"), "volume_percent");
        if (json.has("item") && json.get("item").isJsonObject()) {
            out.track = track(json.getAsJsonObject("item"));
            if (!out.track.id.isEmpty()) out.saved = isSavedTrack(out.track.id);
        }
        return out;
    }

    public List<Playlist> playlists() throws IOException {
        JsonObject json = object(api("GET", "/me/playlists?limit=50", null));
        List<Playlist> out = new ArrayList<Playlist>();
        JsonArray items = array(json, "items");
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            Playlist p = new Playlist();
            p.id = string(item, "id"); p.uri = string(item, "uri"); p.name = string(item, "name");
            if (item.has("owner") && item.get("owner").isJsonObject()) p.owner = string(item.getAsJsonObject("owner"), "display_name");
            JsonObject contents = item.has("items") && item.get("items").isJsonObject() ? item.getAsJsonObject("items")
                    : item.has("tracks") && item.get("tracks").isJsonObject() ? item.getAsJsonObject("tracks") : null;
            if (contents != null) p.tracks = integer(contents, "total");
            JsonArray images = array(item, "images");
            if (images.size() > 0 && images.get(0).isJsonObject()) p.imageUrl = string(images.get(0).getAsJsonObject(), "url");
            out.add(p);
        }
        return out;
    }

    public TrackPage playlistTracks(String id) throws IOException {
        try {
            return trackPage(api("GET", "/playlists/" + encPath(id) + "/items?limit=50", null), true);
        } catch (IOException error) {
            if (error.getMessage() != null && error.getMessage().contains("HTTP 403")) {
                throw new IOException("O Spotify so permite abrir playlists suas ou colaborativas no modo de desenvolvimento");
            }
            throw error;
        }
    }
    public TrackPage savedTracks() throws IOException { return trackPage(api("GET", "/me/tracks?limit=50", null), true); }

    public TrackPage search(String query) throws IOException {
        JsonObject root = object(api("GET", "/search?type=track&limit=10&q=" + enc(query), null));
        return root.has("tracks") ? trackPage(root.getAsJsonObject("tracks").toString(), false) : new TrackPage();
    }

    private TrackPage trackPage(String body, boolean wrapped) {
        JsonObject json = object(body);
        TrackPage out = new TrackPage();
        out.total = integer(json, "total");
        for (JsonElement element : array(json, "items")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (wrapped && item.has("item") && item.get("item").isJsonObject()) item = item.getAsJsonObject("item");
            else if (wrapped && item.has("track") && item.get("track").isJsonObject()) item = item.getAsJsonObject("track");
            Track track = track(item);
            if (!track.uri.isEmpty()) out.tracks.add(track);
        }
        return out;
    }

    public List<Device> devices() throws IOException {
        JsonObject json = object(api("GET", "/me/player/devices", null));
        List<Device> out = new ArrayList<Device>();
        for (JsonElement element : array(json, "devices")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            Device d = new Device(); d.id = string(item, "id"); d.name = string(item, "name"); d.type = string(item, "type");
            d.active = bool(item, "is_active"); d.restricted = bool(item, "is_restricted"); out.add(d);
        }
        return out;
    }

    private String usableDevice() throws IOException {
        Device fallback = null;
        for (Device device : devices()) {
            if (device.restricted || device.id.isEmpty()) continue;
            if (device.active) return device.id;
            if (fallback == null) fallback = device;
        }
        if (fallback == null) throw new IOException("Abra o Spotify no computador ou celular e inicie qualquer musica primeiro");
        api("PUT", "/me/player", "{\"device_ids\":[" + GSON.toJson(fallback.id) + "],\"play\":false}");
        try { Thread.sleep(350L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return fallback.id;
    }

    private void player(String method, String path, String body) throws IOException {
        String device = usableDevice();
        String separator = path.contains("?") ? "&" : "?";
        IOException first;
        try {
            api(method, path + separator + "device_id=" + enc(device), body);
            return;
        } catch (IOException error) {
            if (!isRestriction(error)) throw error;
            first = error;
        }

        // Some Spotify Connect clients report themselves as usable but reject
        // commands addressed explicitly by device id. Let Spotify target the
        // currently active device before attempting a transfer.
        try {
            api(method, path, body);
            return;
        } catch (IOException error) {
            if (!isRestriction(error)) throw error;
        }

        for (Device candidate : devices()) {
            if (candidate.restricted || candidate.id.isEmpty() || candidate.id.equals(device)) continue;
            try {
                api("PUT", "/me/player", "{\"device_ids\":[" + GSON.toJson(candidate.id) + "],\"play\":false}");
                try { Thread.sleep(500L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                api(method, path + separator + "device_id=" + enc(candidate.id), body);
                return;
            } catch (IOException error) {
                if (!isRestriction(error)) throw error;
            }
        }
        throw new IOException("O dispositivo atual do Spotify bloqueou este comando. Troque a reproducao para o celular ou computador e tente novamente (" + first.getMessage() + ")");
    }

    private static boolean isRestriction(IOException error) {
        String message = error.getMessage();
        return message != null && (message.contains("Spotify HTTP 403") || message.toLowerCase(Locale.ROOT).contains("restriction violated"));
    }

    public void play() throws IOException { player("PUT", "/me/player/play", "{}"); }
    public void pause() throws IOException { player("PUT", "/me/player/pause", null); }
    public void next() throws IOException { player("POST", "/me/player/next", null); }
    public void previous() throws IOException { player("POST", "/me/player/previous", null); }
    public void volume(int value) throws IOException { player("PUT", "/me/player/volume?volume_percent=" + Math.max(0, Math.min(100, value)), null); }
    public void shuffle(boolean value) throws IOException { player("PUT", "/me/player/shuffle?state=" + value, null); }
    public void repeat(String value) throws IOException { player("PUT", "/me/player/repeat?state=" + enc(value), null); }
    public void seek(int positionMs) throws IOException { player("PUT", "/me/player/seek?position_ms=" + Math.max(0, positionMs), null); }
    public void playTrack(String uri) throws IOException { playTrack(null, uri); }
    public void playTrack(String contextUri, String trackUri) throws IOException {
        String body = contextUri == null || contextUri.isEmpty()
                ? "{\"uris\":[" + GSON.toJson(trackUri) + "]}"
                : "{\"context_uri\":" + GSON.toJson(contextUri) + ",\"offset\":{\"uri\":" + GSON.toJson(trackUri) + "}}";
        player("PUT", "/me/player/play", body);
    }
    public void playPlaylist(String uri) throws IOException { player("PUT", "/me/player/play", "{\"context_uri\":" + GSON.toJson(uri) + "}"); }
    public void saveTrack(String id, boolean save) throws IOException {
        api(save ? "PUT" : "DELETE", "/me/library", "{\"uris\":[" + GSON.toJson("spotify:track:" + id) + "]}");
    }
    public boolean isSavedTrack(String id) throws IOException {
        String value = api("GET", "/me/library/contains?uris=" + enc("spotify:track:" + id), null);
        JsonElement json = new JsonParser().parse(value);
        return json.isJsonArray() && json.getAsJsonArray().size() > 0 && json.getAsJsonArray().get(0).getAsBoolean();
    }

    private String api(String method, String path, String body) throws IOException {
        HttpURLConnection connection = open(API + path, method);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken());
        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json");
            write(connection, body);
        } else if ("POST".equals(method) || "PUT".equals(method)) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(0);
            connection.getOutputStream().close();
        }
        return read(connection);
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setUseCaches(false);
        return connection;
    }

    private static void write(HttpURLConnection connection, String body) throws IOException {
        connection.setDoOutput(true);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
    }

    private static String read(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = "";
        if (input != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) out.append(line);
            text = out.toString();
        }
        if (code >= 400) throw new IOException("Spotify HTTP " + code + (text.isEmpty() ? "" : ": " + text));
        return text;
    }

    private static Track track(JsonObject item) {
        Track t = new Track();
        t.id = string(item, "id"); t.uri = string(item, "uri"); t.title = string(item, "name"); t.durationMs = integer(item, "duration_ms");
        StringBuilder artists = new StringBuilder();
        for (JsonElement element : array(item, "artists")) {
            if (!element.isJsonObject()) continue;
            if (artists.length() > 0) artists.append(", ");
            artists.append(string(element.getAsJsonObject(), "name"));
        }
        t.artists = artists.toString();
        if (item.has("album") && item.get("album").isJsonObject()) {
            JsonObject album = item.getAsJsonObject("album"); t.album = string(album, "name"); t.albumUri = string(album, "uri");
            JsonArray images = array(album, "images"); if (images.size() > 0 && images.get(0).isJsonObject()) t.imageUrl = string(images.get(0).getAsJsonObject(), "url");
        }
        return t;
    }

    private static JsonObject object(String value) { return value == null || value.isEmpty() ? new JsonObject() : new JsonParser().parse(value).getAsJsonObject(); }
    private static JsonArray array(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray(); }
    private static String string(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : ""; }
    private static int integer(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0; }
    private static boolean bool(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean(); }
    private static String enc(String value) throws UnsupportedEncodingException { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
    private static String encPath(String value) throws UnsupportedEncodingException { return enc(value).replace("+", "%20"); }
}
