package rip.moth.cocoonshell.froglog;

import android.util.Log;

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

    public static final class Stats {
        public final String monthLine;
        public final String yearLine;
        public final String rateLine;
        public final String compactHours;
        public final String error;
        /** Null when the request failed. */
        public FroglogStatsSummary summary;

        public Stats(String monthLine, String yearLine, String rateLine, String error) {
            this(monthLine, yearLine, rateLine, error, "0h");
        }

        public Stats(String monthLine, String yearLine, String rateLine, String error, String compactHours) {
            this.monthLine = monthLine;
            this.yearLine = yearLine;
            this.rateLine = rateLine;
            this.compactHours = compactHours == null || compactHours.isEmpty() ? "0h" : compactHours;
            this.error = error;
        }
    }

    public static final class Hit {
        public final String title;
        public final String platform;
        public final String coverUrl;

        public Hit(String title, String platform, String coverUrl) {
            this.title = title;
            this.platform = platform;
            this.coverUrl = coverUrl;
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

    public static Recent recentGames(String token, String username, int limit, String filter) {
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
            return new Recent(FroglogGames.recent(games.body, liveBody, limit, filter), null);
        } catch (Exception e) {
            return new Recent(Collections.<FroglogGame>emptyList(), "Could not reach Froglog");
        }
    }

    /** The signed-in library, including private games. Used to match a Cocoon title before writing. */
    public static Recent library(String token) {
        return library(token, FroglogGames.FILTER_RECENT, Integer.MAX_VALUE);
    }

    /** Signed-in library for the Froglog pod. Private games are included. The widget grid stays public. */
    public static Recent library(String token, String filter, int limit) {
        try {
            HttpResult games = request("GET", BASE + "/games", token, null);
            if (games.code == 401) {
                return new Recent(Collections.<FroglogGame>emptyList(), "Sign in to Froglog again");
            }
            if (games.code < 200 || games.code >= 300) {
                return new Recent(Collections.<FroglogGame>emptyList(),
                        errorMessage(games.body, "Could not load your Froglog library (" + games.code + ")"));
            }
            HttpResult live = request("GET", BASE + "/live-service", token, null);
            String liveBody = (live.code >= 200 && live.code < 300) ? live.body : "[]";
            return new Recent(FroglogGames.recent(games.body, liveBody, limit, filter), null);
        } catch (Exception e) {
            return new Recent(Collections.<FroglogGame>emptyList(), "Could not reach Froglog");
        }
    }

    /** Activity for the signed-in user, already scoped to people they follow. */
    public static String activityJson(String token) throws Exception {
        HttpResult result = request("GET", BASE + "/activity?limit=40", token, null);
        if (result.code == 401) {
            throw new IllegalStateException("Sign in to Froglog again");
        }
        if (result.code < 200 || result.code >= 300) {
            throw new IllegalStateException(errorMessage(result.body, "Could not load Froglog activity (" + result.code + ")"));
        }
        return result.body;
    }

    /**
     * Same payload LilyPad sends so Froglog's Online Now card and profile show
     * the game currently open in Cocoon.
     */
    public static void setNowPlaying(String token, long gameId, String gameType, String title, String startedAt)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("game_id", gameId);
        body.put("game_type", gameType == null || gameType.isEmpty() ? "game" : gameType);
        if (title != null && !title.isEmpty()) {
            body.put("title", title);
        }
        if (startedAt != null && !startedAt.isEmpty()) {
            body.put("started_at", startedAt);
        }
        HttpResult result = request("PUT", BASE + "/users/me/now-playing", token, body.toString());
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not update Froglog presence (" + result.code + ")");
        }
    }

    public static void clearNowPlaying(String token) throws Exception {
        HttpResult result = request("DELETE", BASE + "/users/me/now-playing", token, null);
        if (result.code == 404) {
            return;
        }
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not clear Froglog presence (" + result.code + ")");
        }
    }

    /** Turns on the profile "In Game" row that LilyPad also enables. */
    public static void showCurrentSession(String token, boolean enabled) throws Exception {
        JSONObject body = new JSONObject();
        body.put("showCurrentSession", enabled);
        HttpResult result = request("PUT", BASE + "/users/current-session-visibility", token, body.toString());
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not update Froglog presence visibility (" + result.code + ")");
        }
    }

    /** People the signed-in user follows. Not in the public docs. Null when it cannot be read. */
    public static String followingJson(String token) {
        try {
            HttpResult result = request("GET", BASE + "/users/me/following", token, null);
            if (result.code < 200 || result.code >= 300) {
                return null;
            }
            return result.body;
        } catch (Exception e) {
            return null;
        }
    }

    /** Everyone in game right now, not just follows. Used only to mark a followed user as playing. */
    public static String onlineJson(String token) throws Exception {
        HttpResult result = request("GET", BASE + "/activity/online", token, null);
        if (result.code < 200 || result.code >= 300) {
            return "[]";
        }
        return result.body == null || result.body.isEmpty() ? "[]" : result.body;
    }

    public static Stats stats(String token, boolean withTop) {
        try {
            HttpResult result = request("GET", BASE + "/stats", token, null);
            if (result.code == 401) {
                return new Stats("", "", "", "Sign in to Froglog again");
            }
            if (result.code < 200 || result.code >= 300) {
                return new Stats("", "", "", errorMessage(result.body, "Could not load Froglog stats (" + result.code + ")"));
            }
            FroglogStatsSummary summary = new FroglogStatsSummary(FroglogStatsSummary.today());
            summary.readStats(result.body);
            if (withTop) {
                try {
                    readMonthSessions(token, summary);
                } catch (Exception ignored) {
                    // The figures from /stats still stand without a top game.
                }
            }
            String hours = FroglogGames.hoursLabel(Double.valueOf(summary.monthHours));
            Stats stats = new Stats(
                    periodLine("This month", summary.monthHours, summary.monthCompleted),
                    periodLine("This year", summary.yearHours, summary.yearCompleted),
                    summary.completionRate < 0 ? "Completion —" : "Completion " + summary.completionRate + "%",
                    null, hours == null ? "0h" : hours);
            stats.summary = summary;
            return stats;
        } catch (Exception e) {
            return new Stats("", "", "", "Could not reach Froglog");
        }
    }

    public static Stats stats(String token) {
        return stats(token, false);
    }

    /** Up to three pages of each session list, stopping once a page reaches last month. */
    private static void readMonthSessions(String token, FroglogStatsSummary summary) throws Exception {
        String[] lists = {"/sessions/games", "/sessions/live-service"};
        for (int l = 0; l < lists.length; l++) {
            for (int page = 1; page <= 3; page++) {
                HttpResult result = request("GET", BASE + lists[l] + "?limit=100&page=" + page, token, null);
                if (result.code < 200 || result.code >= 300) {
                    break;
                }
                if (!summary.addSessions(result.body, l == 1)) {
                    break;
                }
            }
        }
        summary.finishTop();
    }

    public static java.util.List<Hit> search(String token, String query) throws Exception {
        String q = URLEncoder.encode(query == null ? "" : query, "UTF-8").replace("+", "%20");
        HttpResult result = request("GET", BASE + "/search?q=" + q, token, null);
        if (result.code < 200 || result.code >= 300) {
            throw new IllegalStateException(errorMessage(result.body, "Froglog search failed (" + result.code + ")"));
        }
        java.util.ArrayList<Hit> hits = new java.util.ArrayList<Hit>();
        org.json.JSONArray array = new org.json.JSONArray(result.body == null || result.body.isEmpty() ? "[]" : result.body);
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            String title = obj.optString("name", "").trim();
            if (title.isEmpty()) {
                continue;
            }
            String platform = null;
            org.json.JSONArray platforms = obj.optJSONArray("platforms");
            if (platforms != null && platforms.length() > 0) {
                JSONObject first = platforms.optJSONObject(0);
                JSONObject nested = first == null ? null : first.optJSONObject("platform");
                if (nested != null) {
                    platform = nested.optString("name", null);
                }
            }
            String cover = obj.optString("background_image", null);
            hits.add(new Hit(title, platform, cover == null || cover.isEmpty() ? null : cover));
        }
        return hits;
    }

    public static final class Logged {
        public final long id;
        public final boolean live;
        /** Froglog's id for the session row, 0 if the response had none. */
        public final long sessionId;

        public Logged(long id, boolean live) {
            this(id, live, 0L);
        }

        public Logged(long id, boolean live, long sessionId) {
            this.id = id;
            this.live = live;
            this.sessionId = sessionId;
        }

        /** {@code game:12:34} or {@code live:12:34}, so a later update can find the row. */
        public String remote() {
            return sessionId <= 0 ? null : (live ? "live:" : "game:") + id + ":" + sessionId;
        }
    }

    /** Sets the hours of a session already on Froglog. {@code remote} is {@link Logged#remote()}. */
    public static void updateSessionHours(String token, String remote, double hours, boolean sessionsPublic)
            throws Exception {
        String[] parts = remote.split(":");
        boolean live = "live".equals(parts[0]);
        long id = Long.parseLong(parts[1]);
        long sessionId = Long.parseLong(parts[2]);
        org.json.JSONArray rows = sessions(token, live, id);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && row.optLong("id", -1L) == sessionId) {
                putSession(token, live, id, row, hours, sessionsPublic);
                return;
            }
        }
        throw new CallException(404, "The Froglog session was removed");
    }

    public static long createGame(String token, String title, String platform, String coverUrl, boolean isPublic) throws Exception {
        FroglogCreate.Outcome outcome = createGameKeyed(token, title, platform, coverUrl, isPublic, null, false);
        if (outcome.needsChoice()) {
            throw new IllegalStateException("Froglog already has this game");
        }
        if (outcome.error != null) {
            throw new IllegalStateException(outcome.error);
        }
        return outcome.createdId;
    }

    /**
     * {@code clientRef} is stable for one Cocoon game so a retry does not create a second entry.
     * {@code confirmNew} sends {@code confirm_action: "new"} when the player wants a separate log.
     */
    public static FroglogCreate.Outcome createGameKeyed(String token, String title, String platform, String coverUrl,
            boolean isPublic, String clientRef, boolean confirmNew) throws Exception {
        JSONObject body = new JSONObject();
        body.put("title", title);
        if (platform != null && !platform.isEmpty()) {
            body.put("platform", platform);
        }
        if (coverUrl != null && !coverUrl.isEmpty()) {
            body.put("cover_image", coverUrl);
            body.put("img", coverUrl);
        }
        body.put("is_public", isPublic);
        if (clientRef != null && !clientRef.isEmpty()) {
            body.put("client_ref", clientRef);
        }
        if (confirmNew) {
            body.put("confirm_action", "new");
        }
        HttpResult result = request("POST", BASE + "/games", token, body.toString());
        return FroglogCreate.interpret(result.code, result.body);
    }

    public static Logged logSession(String token, FroglogGame game, String date, double hours, String syncRef)
            throws Exception {
        return logSession(token, game, date, hours, syncRef, "Logged from Cocoon", true);
    }

    /**
     * The signed-in library row for this title. Now-playing can accept a catalog id
     * that {@code GET /games/:id} and {@code POST /games/:id/sessions} reject.
     */
    public static FroglogGame ownedGame(String token, String title, String platform) {
        Recent recent = library(token);
        if (recent == null || recent.games == null) {
            return null;
        }
        return FroglogMatch.best(recent.games, title, platform);
    }

    public static Logged logSession(String token, FroglogGame game, String date, double hours, String syncRef,
            String notes) throws Exception {
        return logSession(token, game, date, hours, syncRef, notes, true);
    }

    public static Logged logSession(String token, FroglogGame game, String date, double hours, String syncRef,
            String notes, boolean sessionsPublic) throws Exception {
        boolean live = game.live;
        long id = game.id;
        FroglogGame owned = ownedGame(token, game.title, game.platform);
        if (owned != null) {
            live = owned.live;
            id = owned.id;
        }
        if (!live) {
            try {
                JSONObject raw = getGame(token, id);
                JSONObject payload = FroglogTracking.preparePayload(raw, date, true, sessionsPublic);
                if (payload != null) {
                    putGame(token, id, payload);
                }
            } catch (CallException missing) {
                if (missing.code != 404) {
                    throw missing;
                }
                Long recovered = uniqueLiveId(token, game.title);
                if (recovered != null) {
                    live = true;
                    id = recovered.longValue();
                }
            }
        }
        try {
            long session = postSession(token, live, id, date, hours, syncRef, notes, sessionsPublic);
            return new Logged(id, live, session);
        } catch (CallException missing) {
            if (missing.code != 404 || live) {
                throw missing;
            }
            Long recovered = uniqueLiveId(token, game.title);
            if (recovered == null) {
                throw missing;
            }
            long session = postSession(token, true, recovered.longValue(), date, hours, syncRef, notes, sessionsPublic);
            return new Logged(recovered.longValue(), true, session);
        }
    }

    /**
     * Turns on session tracking and stamps {@code start_date} when the library row
     * is still empty. Presence can call this before a finished session exists.
     */
    public static void ensureTracking(String token, long id, String date, boolean sessionsPublic) throws Exception {
        JSONObject raw = getGame(token, id);
        JSONObject payload = FroglogTracking.preparePayload(raw, date, true, sessionsPublic);
        if (payload != null) {
            putGame(token, id, payload);
        }
    }

    /** Froglog has no {@code GET /games/:id}. LilyPad reads the library list and picks the row. */
    private static JSONObject getGame(String token, long id) throws Exception {
        HttpResult result = request("GET", BASE + "/games", token, null);
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not load the Froglog library (" + result.code + ")");
        }
        org.json.JSONArray games = new org.json.JSONArray(result.body.isEmpty() ? "[]" : result.body);
        for (int i = 0; i < games.length(); i++) {
            JSONObject game = games.optJSONObject(i);
            if (game != null && game.optLong("id", -1L) == id) {
                return game;
            }
        }
        throw new CallException(404, "Game " + id + " is not in the Froglog library");
    }

    public static org.json.JSONArray gameSessions(String token, boolean live, long id) throws Exception {
        return sessions(token, live, id);
    }

    private static org.json.JSONArray sessions(String token, boolean live, long id) throws Exception {
        HttpResult result = request("GET", BASE + (live ? "/live-service/" : "/games/") + id + "/sessions", token, null);
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not load the sessions (" + result.code + ")");
        }
        return new org.json.JSONArray(result.body.isEmpty() ? "[]" : result.body);
    }

    /** The website's session edit sends every field, so a partial body is never used. */
    private static void putSession(String token, boolean live, long id, JSONObject row, double hours, boolean isPublic)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("date", row.optString("date", ""));
        body.put("hours", hours);
        body.put("notes", row.isNull("notes") ? JSONObject.NULL : row.opt("notes"));
        body.put("is_public", isPublic);
        body.put("spoiler", row.optBoolean("spoiler", false));
        String path = BASE + (live ? "/live-service/" : "/games/") + id + "/sessions/" + row.optLong("id");
        HttpResult result = request("PUT", path, token, body.toString());
        Log.i("FroglogWidget", "SESSION PUT " + path + " " + body + " -> " + result.code);
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not update the session (" + result.code + ")");
        }
    }

    /**
     * Fixes a game Cocoon logged to before tracking was set up: session tracking on, a start
     * date so it reads In Progress, and Cocoon's own sessions public. A finished game stays finished.
     */
    public static void repairGame(String token, long id, boolean live, boolean sessionsPublic) throws Exception {
        org.json.JSONArray rows = sessions(token, live, id);
        String earliest = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String date = row == null ? "" : row.optString("date", "");
            if (date.length() >= 10 && (earliest == null || date.substring(0, 10).compareTo(earliest) < 0)) {
                earliest = date.substring(0, 10);
            }
        }
        if (!live) {
            if (earliest == null) {
                earliest = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            }
            JSONObject payload = FroglogTracking.preparePayload(getGame(token, id), earliest, false, sessionsPublic);
            if (payload != null) {
                putGame(token, id, payload);
                Log.i("FroglogWidget", "Repaired Froglog game " + id + " tracking and start date");
                rows = sessions(token, false, id);
            }
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || row.optBoolean("is_public", false) == sessionsPublic) {
                continue;
            }
            if (!row.optString("sync_ref", "").startsWith("cocoon:")) {
                continue;
            }
            putSession(token, live, id, row, row.optDouble("hours", 0), sessionsPublic);
        }
    }

    private static void putGame(String token, long id, JSONObject body) throws Exception {
        HttpResult result = request("PUT", BASE + "/games/" + id, token, body.toString());
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not update the Froglog game (" + result.code + ")");
        }
    }

    private static long postSession(String token, boolean live, long id, String date, double hours, String syncRef,
            String notes, boolean sessionsPublic) throws Exception {
        JSONObject body = new JSONObject();
        body.put("date", date);
        body.put("hours", hours);
        body.put("notes", notes == null || notes.isEmpty() ? "Logged from Cocoon" : notes);
        body.put("spoiler", false);
        body.put("is_public", sessionsPublic);
        if (syncRef != null) {
            body.put("sync_ref", syncRef);
        }
        String path = BASE + (live ? "/live-service/" : "/games/") + id + "/sessions";
        Log.i("FroglogWidget", "SESSION POST " + path + " " + body);
        HttpResult result = request("POST", path, token, body.toString());
        Log.i("FroglogWidget", "SESSION RESP " + result.code + " " + result.body);
        if (result.code < 200 || result.code >= 300) {
            throw call(result, "Could not log the session (" + result.code + ")");
        }
        try {
            return new JSONObject(result.body).optLong("id", 0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static Long uniqueLiveId(String token, String title) throws Exception {
        HttpResult live = request("GET", BASE + "/live-service", token, null);
        if (live.code < 200 || live.code >= 300) {
            return null;
        }
        List<FroglogGame> games = FroglogGames.recent("[]", live.body, Integer.MAX_VALUE, FroglogGames.FILTER_LIVE);
        return FroglogTracking.uniqueLiveId(games, title);
    }

    private static CallException call(HttpResult result, String fallback) {
        return new CallException(result.code, errorMessage(result.body, fallback));
    }

    private static final class CallException extends Exception {
        final int code;

        CallException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static String periodLine(String label, double hours, int completed) {
        String hoursLabel = FroglogGames.hoursLabel(Double.valueOf(hours));
        return label + ": " + (hoursLabel == null ? "0h" : hoursLabel) + " · " + completed + " finished";
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
