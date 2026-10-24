package crewx.spotify;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import crewx.spotify.SpotifyModels.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class SpotifyWebServer {
    private static final Gson JSON = new Gson();
    private static SpotifyWebServer instance;
    private final SpotifyService service;
    private final HttpServer server;

    private SpotifyWebServer(SpotifyService service) throws IOException {
        this.service = service;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "CrewX Spotify Web"); t.setDaemon(true); return t; }));
        server.start();
    }

    public static synchronized SpotifyWebServer start(SpotifyService service) throws IOException {
        if (instance == null) instance = new SpotifyWebServer(service);
        return instance;
    }

    public static synchronized void stop() {
        if (instance != null) {
            instance.server.stop(0);
            instance = null;
        }
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/spotify-gui/index.html?port=" + server.getAddress().getPort(); }

    private void handle(HttpExchange x) throws IOException {
        try {
            String path = x.getRequestURI().getPath();
            if ("OPTIONS".equals(x.getRequestMethod())) { send(x, 204, "application/json", ""); return; }
            if (path.equals("/") || path.equals("/spotify-gui/index.html")) { servePage(x); return; }
            if (path.equals("/api/v1/client/spotify")) { sendJson(x, now()); return; }
            if (path.equals("/api/v1/client/spotify/playlists")) { sendJson(x, playlists()); return; }
            if (path.equals("/api/v1/client/spotify/playlist")) { sendJson(x, playlist(param(x.getRequestURI().getRawQuery(), "id"))); return; }
            if (path.equals("/api/v1/client/spotify/search")) { sendJson(x, search(param(x.getRequestURI().getRawQuery(), "q"))); return; }
            JsonObject body = readJson(x);
            if (path.equals("/api/v1/client/spotify/control")) { control(body); ok(x); return; }
            if (path.equals("/api/v1/client/spotify/play")) { play(body); ok(x); return; }
            if (path.equals("/api/v1/client/spotify/like")) { service.saveTrack(str(body,"trackId"), body.has("save") && body.get("save").getAsBoolean()); ok(x); return; }
            if (path.equals("/api/v1/client/action") && "spotify-connect".equals(str(body,"action"))) { SpotifyAuth.connect(service, e -> {}); ok(x); return; }
            send(x, 404, "application/json", "{\"error\":\"not found\"}");
        } catch (Throwable e) { send(x, 500, "application/json", JSON.toJson(Collections.singletonMap("error", e.getMessage()))); }
    }

    private void servePage(HttpExchange x) throws IOException {
        InputStream in = SpotifyWebServer.class.getResourceAsStream("/assets/minecraft/fdpclient/spotify-gui/index.html");
        if (in == null) { send(x, 404, "text/plain", "Spotify UI missing"); return; }
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) >= 0) out.write(b,0,n); in.close();
        byte[] bytes = out.toByteArray(); headers(x); x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8"); x.sendResponseHeaders(200, bytes.length);
        try (OutputStream os=x.getResponseBody()) { os.write(bytes); }
    }

    private JsonObject now() throws IOException {
        JsonObject o=new JsonObject(); o.addProperty("connection", service.isConfigured()?"CONNECTED":"DISCONNECTED"); o.addProperty("local",false);
        Playback p=service.playback(); if(p==null) return o;
        o.addProperty("isPlaying",p.playing); o.addProperty("progressMs",p.progressMs); o.addProperty("shuffle",p.shuffle);
        o.addProperty("volumePercent",p.volumePercent);
        o.addProperty("repeat", "track".equals(p.repeat)?"ONE":"context".equals(p.repeat)?"ALL":"OFF");
        if(p.track!=null) o.add("track", track(p.track,p.saved)); return o;
    }
    private JsonObject playlists() throws IOException {
        JsonObject o=new JsonObject(); o.addProperty("connection",service.isConfigured()?"CONNECTED":"DISCONNECTED"); o.addProperty("local",false); JsonArray a=new JsonArray();
        JsonObject liked=new JsonObject(); liked.addProperty("id","__liked__"); liked.addProperty("name","Liked Songs"); liked.addProperty("owner","Spotify"); liked.addProperty("trackCount",-1); liked.addProperty("imageUrl",""); liked.addProperty("uri",""); liked.addProperty("likedSongs",true); a.add(liked);
        for(Playlist p:service.playlists()){ JsonObject j=new JsonObject(); j.addProperty("id",p.id);j.addProperty("name",p.name);j.addProperty("owner",p.owner);j.addProperty("trackCount",p.tracks);j.addProperty("imageUrl",p.imageUrl);j.addProperty("uri",p.uri);j.addProperty("likedSongs",false);a.add(j); }
        o.add("playlists",a); return o;
    }
    private JsonObject playlist(String id) throws IOException { TrackPage page="__liked__".equals(id)?service.savedTracks():service.playlistTracks(id); JsonArray a=new JsonArray(); for(Track t:page.tracks)a.add(track(t,"__liked__".equals(id))); JsonObject o=new JsonObject();o.add("tracks",a);return o; }
    private JsonObject search(String query) throws IOException { TrackPage page=service.search(query); JsonArray a=new JsonArray(); for(Track t:page.tracks)a.add(track(t,false)); JsonObject o=new JsonObject();o.add("tracks",a);return o; }
    private JsonObject track(Track t,boolean liked){JsonObject j=new JsonObject();j.addProperty("id",t.id);j.addProperty("title",t.title);j.addProperty("artists",t.artists);j.addProperty("album",t.album);j.addProperty("albumUri",t.albumUri);j.addProperty("addedAt","");j.addProperty("durationMs",t.durationMs);j.addProperty("coverUrl",t.imageUrl);j.addProperty("uri",t.uri);j.addProperty("liked",liked);return j;}
    private void control(JsonObject b)throws IOException{String a=str(b,"action");if("playPause".equals(a)){if(b.has("playing")&&b.get("playing").getAsBoolean())service.play();else if(b.has("playing"))service.pause();else{Playback p=service.playback();if(p!=null&&p.playing)service.pause();else service.play();}}else if("next".equals(a))service.next();else if("previous".equals(a))service.previous();else if("shuffle".equals(a)){boolean state=b.has("state")?b.get("state").getAsBoolean():true;service.shuffle(state);}else if("repeat".equals(a)){String state=str(b,"state");service.repeat("ONE".equals(state)?"track":"ALL".equals(state)?"context":"off");}else if("seek".equals(a)&&b.has("positionMs"))service.seek(b.get("positionMs").getAsInt());else if("volume".equals(a)&&b.has("value"))service.volume(b.get("value").getAsInt());else throw new IOException("Comando Spotify desconhecido: "+a);}
    private void play(JsonObject b)throws IOException{String c=str(b,"contextUri"),t=str(b,"trackUri");if(!c.isEmpty()&&t.isEmpty())service.playPlaylist(c);else service.playTrack(c,t);}
    private static JsonObject readJson(HttpExchange x)throws IOException{InputStream in=x.getRequestBody();InputStreamReader r=new InputStreamReader(in,StandardCharsets.UTF_8);JsonElement e=new JsonParser().parse(r);return e.isJsonObject()?e.getAsJsonObject():new JsonObject();}
    private static String str(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    private static String param(String q,String key)throws Exception{if(q==null)return"";for(String s:q.split("&")){String[]p=s.split("=",2);if(URLDecoder.decode(p[0],"UTF-8").equals(key))return URLDecoder.decode(p.length>1?p[1]:"","UTF-8");}return"";}
    private static void ok(HttpExchange x)throws IOException{send(x,200,"application/json","{\"ok\":true}");}
    private static void headers(HttpExchange x){x.getResponseHeaders().set("Access-Control-Allow-Origin","*");x.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");x.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,OPTIONS");x.getResponseHeaders().set("Cache-Control","no-store");}
    private static void sendJson(HttpExchange x,JsonObject o)throws IOException{send(x,200,"application/json; charset=utf-8",JSON.toJson(o));}
    private static void send(HttpExchange x,int code,String type,String body)throws IOException{byte[]b=body.getBytes(StandardCharsets.UTF_8);headers(x);x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(code,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
}
