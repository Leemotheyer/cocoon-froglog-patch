package rip.moth.cocoonshell.froglog;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Froglog production API. See https://wiki.froglog.co.uk/Api/Documentation */
public final class FroglogClient {
    public static final String BASE = "https://api.froglog.co.uk/api";

    private FroglogClient() {}

    public static final class Session {
        public final String token;
        public final String username;

        public Session(String token, String username) {
            this.token = token;
            this.username = username;
        }
    }

    public static final class Recent {
        public final List<FroglogGame> games;
        public final String error;

        public Recent(List<FroglogGame> games, String error) {
            this.games = games;
            this.error = error;
        }
    }

    public static Session login(String username, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        HttpResult result = request("POST", BASE + "/auth/login", null, body.toString());
        if (result.code == 401) {
            throw new IllegalArgumentException("Those Froglog details were not accepted");
        }
        if (result.code == 400) {
            throw new IllegalArgumentException(errorMessage(result.body, "Could not sign in"));
        }
        if (result.code < 200 || result.code >= 300) {
            throw new IllegalStateException(errorMessage(result.body, "Froglog login failed (" + result.code + ")"));
        }
        JSONObject json = new JSONObject(result.body);
        String token = json.optString("token", "");
        String name = json.optString("username", username);
        if (token.isEmpty()) {
            throw new IllegalStateException("Froglog did not return a token");
        }
        return new Session(token, name);
    }

    public static Recent recentGames(String token, String username, int limit) {
        try {
            String encoded = URLEncoder.encode(username, "UTF-8").replace("+", "%20");
            HttpResult games = request("GET", BASE + "/users/" + encoded + "/games", token, null);
            if (games.code == 401) {
                return new Recent(Collections.<FroglogGame>emptyList(), "Sign in to Froglog again");
            }
            if (games.code == 404) {
                return new Recent(Collections.<FroglogGame>emptyList(), "Froglog user not found");
            }
            if (games.code < 200 || games.code >= 300) {
                return new Recent(Collections.<FroglogGame>emptyList(),
                        errorMessage(games.body, "Could not load games (" + games.code + ")"));
            }
            HttpResult live = request("GET", BASE + "/users/" + encoded + "/live-service", token, null);
            String liveBody = (live.code >= 200 && live.code < 300) ? live.body : "[]";
            return new Recent(FroglogGames.recent(games.body, liveBody, limit), null);
        } catch (Exception e) {
            return new Recent(Collections.<FroglogGame>emptyList(), "Could not reach Froglog");
        }
    }

    private static String errorMessage(String body, String fallback) {
        try {
            JSONObject json = new JSONObject(body);
            String error = json.optString("error", "");
            if (!error.isEmpty()) {
                return error;
            }
            String message = json.optString("message", "");
            if (!message.isEmpty()) {
                return message;
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return fallback;
    }

    private static HttpResult request(String method, String url, String token, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "CocoonFroglogWidget/1.0");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (jsonBody != null) {
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String body = stream == null ? "" : read(stream);
        conn.disconnect();
        return new HttpResult(code, body);
    }

    private static String read(InputStream stream) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            stream.close();
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
