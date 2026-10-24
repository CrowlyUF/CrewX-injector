package crewx.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.*;

public final class SpotifyAuth {
    public interface Callback { void complete(String error); }
    private SpotifyAuth() {}

    public static void connect(final SpotifyService service, final Callback callback) {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                HttpServer server = null;
                try {
                    if (service.getClientId().trim().isEmpty()) throw new IOException("Informe o Client ID do Spotify primeiro");
                    final String verifier = verifier();
                    final String challenge = challenge(verifier);
                    final String state = verifier().substring(0, 24);
                    final String redirect = "http://127.0.0.1:43791/spotify-oauth-callback";
                    final CompletableFuture<String> code = new CompletableFuture<String>();
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 43791), 0);
                    server.createContext("/spotify-oauth-callback", exchange -> handle(exchange, state, code));
                    server.setExecutor(Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "CrewX Spotify OAuth"); t.setDaemon(true); return t; }));
                    server.start();
                    String scopes = "user-read-currently-playing user-read-playback-state user-modify-playback-state playlist-read-private playlist-read-collaborative user-library-read user-library-modify";
                    String url = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(service.getClientId())
                            + "&redirect_uri=" + enc(redirect) + "&scope=" + enc(scopes) + "&state=" + enc(state)
                            + "&code_challenge_method=S256&code_challenge=" + enc(challenge);
                    Desktop.getDesktop().browse(new URI(url));
                    service.exchangeCode(code.get(180, TimeUnit.SECONDS), verifier, redirect);
                    callback.complete(null);
                } catch (Throwable error) {
                    callback.complete(error.getMessage() == null ? error.toString() : error.getMessage());
                } finally {
                    if (server != null) server.stop(0);
                }
            }
        }, "CrewX Spotify Login");
        thread.setDaemon(true);
        thread.start();
    }

    private static void handle(HttpExchange exchange, String expectedState, CompletableFuture<String> result) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String state = parameter(query, "state");
        String code = parameter(query, "code");
        String error = parameter(query, "error");
        boolean ok = expectedState.equals(state) && code != null && error == null;
        String html = ok ? "<h2>Spotify conectado ao CrewX.</h2><p>Voce pode fechar esta pagina.</p>" : "<h2>Falha ao conectar Spotify.</h2>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(ok ? 200 : 400, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        if (ok) result.complete(code); else result.completeExceptionally(new IOException(error == null ? "Resposta OAuth invalida" : error));
    }

    private static String parameter(String query, String name) throws UnsupportedEncodingException {
        if (query == null) return null;
        for (String part : query.split("&")) {
            int split = part.indexOf('=');
            String key = split < 0 ? part : part.substring(0, split);
            if (name.equals(URLDecoder.decode(key, "UTF-8"))) return URLDecoder.decode(split < 0 ? "" : part.substring(split + 1), "UTF-8");
        }
        return null;
    }

    private static String verifier() {
        byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String enc(String value) throws UnsupportedEncodingException { return URLEncoder.encode(value, "UTF-8"); }
}
