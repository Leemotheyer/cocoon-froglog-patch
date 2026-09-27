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

    public static List<FroglogGame> recent(String gamesJson, String liveJson, int limit) {
        ArrayList<FroglogGame> all = new ArrayList<FroglogGame>();
        collect(gamesJson, false, all);
        collect(liveJson, true, all);
        Collections.sort(all, new Comparator<FroglogGame>() {
            @Override
            public int compare(FroglogGame a, FroglogGame b) {
                return Long.compare(b.sortKey, a.sortKey);
            }
        });
        if (all.size() > limit) {
            return new ArrayList<FroglogGame>(all.subList(0, limit));
        }
        return all;
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
            out.add(new FroglogGame(title, text(obj, "platform"), cover, meta(when, hours, status), rank));
        }
    }

    static String meta(String when, Double hours, String status) {
        String hoursLabel = hoursLabel(hours);
        if (when != null && hoursLabel != null) {
            return when + " · " + hoursLabel;
        }
        if (when != null) {
            return when;
        }
        if (hoursLabel != null && status != null) {
            return status + " · " + hoursLabel;
        }
        if (hoursLabel != null) {
            return hoursLabel;
        }
        if (status != null) {
            return status;
        }
        return "";
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
