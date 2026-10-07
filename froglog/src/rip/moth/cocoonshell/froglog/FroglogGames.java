package rip.moth.cocoonshell.froglog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Turns Froglog {@code /users/:username/games} and {@code /live-service} payloads
 * into the handful of most recently played public games.
 */
public final class FroglogGames {
    private FroglogGames() {}

    public static final String FILTER_RECENT = "recent";
    public static final String FILTER_PROGRESS = "progress";
    public static final String FILTER_COMPLETED = "completed";
    public static final String FILTER_LIVE = "live";

    public static List<FroglogGame> recent(String gamesJson, String liveJson, int limit) {
        return recent(gamesJson, liveJson, limit, FILTER_RECENT);
    }

    public static List<FroglogGame> recent(String gamesJson, String liveJson, int limit, String filter) {
        ArrayList<FroglogGame> all = new ArrayList<FroglogGame>();
        String mode = filter == null ? FILTER_RECENT : filter;
        if (!FILTER_LIVE.equals(mode)) {
            collect(gamesJson, false, all);
        }
        if (FILTER_RECENT.equals(mode) || FILTER_LIVE.equals(mode)) {
            collect(liveJson, true, all);
        }
        ArrayList<FroglogGame> kept = new ArrayList<FroglogGame>();
        for (FroglogGame game : all) {
            if (accepts(game, mode)) {
                kept.add(game);
            }
        }
        Collections.sort(kept, new Comparator<FroglogGame>() {
            @Override
            public int compare(FroglogGame a, FroglogGame b) {
                return Long.compare(b.sortKey, a.sortKey);
            }
        });
        if (kept.size() > limit) {
            return new ArrayList<FroglogGame>(kept.subList(0, limit));
        }
        return kept;
    }

    public static String nextFilter(String filter) {
        if (FILTER_PROGRESS.equals(filter)) {
            return FILTER_COMPLETED;
        }
        if (FILTER_COMPLETED.equals(filter)) {
            return FILTER_LIVE;
        }
        if (FILTER_LIVE.equals(filter)) {
            return FILTER_RECENT;
        }
        return FILTER_PROGRESS;
    }

    public static String filterLabel(String filter) {
        if (FILTER_PROGRESS.equals(filter)) {
            return "Playing";
        }
        if (FILTER_COMPLETED.equals(filter)) {
            return "Finished";
        }
        if (FILTER_LIVE.equals(filter)) {
            return "Live";
        }
        return "Recent";
    }

    private static boolean accepts(FroglogGame game, String filter) {
        if (FILTER_PROGRESS.equals(filter)) {
            return !game.live && statusIs(game.status, "In Progress");
        }
        if (FILTER_COMPLETED.equals(filter)) {
            return !game.live && statusIs(game.status, "Completed");
        }
        return true;
    }

    private static boolean statusIs(String status, String expected) {
        return status != null && expected.equalsIgnoreCase(status.trim());
    }

    private static void collect(String json, boolean live, List<FroglogGame> out) {
        if (json == null) {
            return;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '[') {
            return;
        }
        JSONArray array;
        try {
            array = new JSONArray(trimmed);
        } catch (Exception ignored) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            String title = text(obj, "title");
            if (title == null) {
                title = text(obj, "name");
            }
            if (title == null || title.isEmpty()) {
                continue;
            }
            String session = text(obj, "last_session_date");
            String end = text(obj, "end_date");
            String start = text(obj, "start_date");
            String created = text(obj, "created_at");
            String when = firstDate(session, end, start, created);
            long rank = rank(session, end, start, created);
            Double hours = number(obj, "total_hours");
            if (hours == null) {
                hours = number(obj, "hours_played");
            }
            String status = live ? text(obj, "live_service_status") : text(obj, "status");
            String cover = text(obj, "cover_image");
            if (cover == null) {
                cover = text(obj, "img");
            }
            Double rating = number(obj, "rating");
            int sessions = obj.optInt("session_count", 0);
            String platform = text(obj, "platform");
            out.add(new FroglogGame(obj.optLong("id", -1), live, title, platform, cover, status,
                    text(obj, "review"), rating, sessions, meta(platform, status, rating, sessions, hours, when), rank,
                    obj.toString()));
        }
    }

    static String meta(String platform, String status, Double rating, int sessions, Double hours, String when) {
        ArrayList<String> parts = new ArrayList<String>();
        if (platform != null) {
            parts.add(platform);
        }
        String statusLabel = statusLabel(status);
        if (statusLabel != null) {
            parts.add(statusLabel);
        }
        if (rating != null && rating > 0) {
            double stars = rating > 5 ? Math.round(rating / 2.0) / 10.0 : rating.doubleValue();
            parts.add("★" + trimTrailingZero(stars));
        }
        if (sessions > 0) {
            parts.add(sessions == 1 ? "1 session" : sessions + " sessions");
        }
        String hoursLabel = hoursLabel(hours);
        if (hoursLabel != null) {
            parts.add(hoursLabel);
        } else if (when != null) {
            parts.add(when);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(" · ");
            }
            out.append(parts.get(i));
        }
        return out.toString();
    }

    static String statusLabel(String status) {
        if (status == null) {
            return null;
        }
        if ("active".equalsIgnoreCase(status)) {
            return "Live";
        }
        if ("dormant".equalsIgnoreCase(status)) {
            return "Dormant";
        }
        return status;
    }

    public static String hoursLabel(Double hours) {
        if (hours == null || hours <= 0) {
            return null;
        }
        if (Math.abs(hours - Math.rint(hours)) < 0.05) {
            return ((long) Math.rint(hours)) + "h";
        }
        return trimTrailingZero(hours) + "h";
    }

    /** Cocoon's Recently played format: 45m, 3h, 3h 20m. Past 100 hours the minutes are dropped. */
    public static String duration(double hours) {
        long minutes = Double.isNaN(hours) || hours <= 0 ? 0 : Math.round(hours * 60.0);
        if (minutes < 60) {
            return minutes + "m";
        }
        long whole = minutes / 60;
        long rest = minutes % 60;
        if (rest == 0 || whole >= 100) {
            return whole + "h";
        }
        return whole + "h " + rest + "m";
    }

    /** "2026-10-03" to "Oct 3". Anything else comes back unchanged. */
    public static String shortDate(String day) {
        long key = dateKey(day);
        if (key <= 0) {
            return day;
        }
        int month = (int) (key / 100 % 100);
        int date = (int) (key % 100);
        String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        if (month < 1 || month > 12) {
            return day;
        }
        return names[month - 1] + " " + date;
    }

    /** One short line for a widget row: play time, then status or the last session date. */
    public static String widgetMeta(FroglogGame game) {
        if (game.json == null) {
            return game.meta;
        }
        JSONObject obj;
        try {
            obj = new JSONObject(game.json);
        } catch (Exception ignored) {
            return game.meta;
        }
        Double hours = number(obj, "total_hours");
        if (hours == null) {
            hours = number(obj, "hours_played");
        }
        ArrayList<String> parts = new ArrayList<String>();
        if (hours != null && hours > 0) {
            parts.add(duration(hours));
        }
        String status = statusLabel(game.status);
        if (status != null) {
            parts.add(status);
        } else {
            String when = firstDate(text(obj, "last_session_date"), text(obj, "end_date"),
                    text(obj, "start_date"));
            if (when != null) {
                parts.add(shortDate(when));
            }
        }
        if (parts.isEmpty()) {
            return game.platform == null ? "" : game.platform;
        }
        return parts.size() == 1 ? parts.get(0) : parts.get(0) + " · " + parts.get(1);
    }

    private static String trimTrailingZero(double hours) {
        String text = String.format(java.util.Locale.US, "%.1f", hours);
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    /** Session dates outrank completion dates, which outrank library dates. */
    static long rank(String session, String end, String start, String created) {
        long sessionKey = dateKey(session);
        if (sessionKey > 0) {
            return 3_0000_0000L + sessionKey;
        }
        long endKey = dateKey(end);
        if (endKey > 0) {
            return 2_0000_0000L + endKey;
        }
        long startKey = dateKey(start);
        if (startKey > 0) {
            return 1_0000_0000L + startKey;
        }
        return dateKey(created);
    }

    static long dateKey(String raw) {
        if (raw == null) {
            return 0;
        }
        String value = raw.trim();
        if (value.length() >= 10
                && Character.isDigit(value.charAt(0))
                && value.charAt(4) == '-'
                && value.charAt(7) == '-') {
            try {
                int year = Integer.parseInt(value.substring(0, 4));
                int month = Integer.parseInt(value.substring(5, 7));
                int day = Integer.parseInt(value.substring(8, 10));
                return year * 10000L + month * 100L + day;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String firstDate(String... values) {
        String best = null;
        long bestKey = 0;
        for (String value : values) {
            long key = dateKey(value);
            if (key > bestKey) {
                bestKey = key;
                best = value.trim().substring(0, 10);
            }
        }
        return best;
    }

    private static String text(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return null;
        }
        String value = obj.optString(key, "").trim();
        return value.isEmpty() || "null".equals(value) ? null : value;
    }

    private static Double number(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return null;
        }
        double value = obj.optDouble(key, Double.NaN);
        if (Double.isNaN(value)) {
            return null;
        }
        return Double.valueOf(value);
    }
}
