package rip.moth.cocoonshell.froglog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Turns one Froglog library row into labelled lines for the game screen. Known fields come
 * first in a fixed order, then any other readable field the API sent, so nothing is hidden.
 */
public final class FroglogGameInfo {
    private FroglogGameInfo() {}

    /** Key, label. A key may be a {@code |}-separated list of alternatives. */
    private static final String[][] KNOWN = {
            {"status|live_service_status", "Status"},
            {"platform", "Platform"},
            {"total_hours|hours_played", "Hours played"},
            {"session_count", "Sessions"},
            {"rating", "Rating"},
            {"start_date", "Started"},
            {"end_date", "Finished"},
            {"last_session_date", "Last played"},
            {"genre|genres", "Genre"},
            {"developer|developers", "Developer"},
            {"publisher|publishers", "Publisher"},
            {"release_date|release_year|year", "Released"},
            {"format|ownership", "Format"},
            {"store", "Store"},
            {"tags", "Tags"},
            {"notes", "Notes"},
            {"created_at", "Added"},
    };

    private static final String[] HIDDEN = {
            "id", "title", "name", "review", "cover_image", "img", "user_id", "username",
            "session_tracking", "sessions_public", "initial_session_hours", "public_session_count",
            "status_override", "updated_at", "sort_order", "position", "client_ref", "sync_ref",
            "dnf", "is_public", "private", "game_type",
    };

    public static List<String[]> rows(JSONObject game) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        if (game == null) {
            return out;
        }
        TreeSet<String> used = new TreeSet<String>();
        for (int i = 0; i < KNOWN.length; i++) {
            String[] keys = KNOWN[i][0].split("\\|");
            for (int k = 0; k < keys.length; k++) {
                used.add(keys[k]);
            }
            for (int k = 0; k < keys.length; k++) {
                String value = value(keys[k], game.opt(keys[k]));
                if (value != null) {
                    out.add(new String[] {KNOWN[i][1], value});
                    break;
                }
            }
        }
        if (game.optBoolean("dnf", false) && !"DNF".equalsIgnoreCase(game.optString("status", ""))) {
            out.add(new String[] {"Did not finish", "Yes"});
        }
        TreeSet<String> rest = new TreeSet<String>();
        Iterator<String> keys = game.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!used.contains(key) && !hidden(key)) {
                rest.add(key);
            }
        }
        for (String key : rest) {
            String value = value(key, game.opt(key));
            if (value != null) {
                out.add(new String[] {label(key), value});
            }
        }
        return out;
    }

    /** One logged session as {date, length, notes}. Notes may be empty. */
    public static List<String[]> sessions(JSONArray rows) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        if (rows == null) {
            return out;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String date = date(row.optString("date", ""));
            double hours = row.optDouble("hours", Double.NaN);
            String length = Double.isNaN(hours) ? "" : FroglogGames.hoursLabel(Double.valueOf(hours));
            String notes = row.isNull("notes") ? "" : row.optString("notes", "").trim();
            out.add(new String[] {date == null ? "" : date, length == null ? "" : length, notes});
        }
        java.util.Collections.sort(out, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return b[0].compareTo(a[0]);
            }
        });
        return out;
    }

    static String value(String key, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof Boolean) {
            return ((Boolean) raw).booleanValue() ? "Yes" : "No";
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                String text = item instanceof JSONObject ? ((JSONObject) item).optString("name", "") : String.valueOf(item);
                text = text.trim();
                if (text.isEmpty() || "null".equals(text)) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(text);
            }
            return out.length() == 0 ? null : out.toString();
        }
        if (raw instanceof JSONObject) {
            String name = ((JSONObject) raw).optString("name", "").trim();
            return name.isEmpty() ? null : name;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equals(text) || text.startsWith("http://") || text.startsWith("https://")
                || text.startsWith("/uploads/")) {
            return null;
        }
        if ("total_hours".equals(key) || "hours_played".equals(key)) {
            try {
                return FroglogGames.hoursLabel(Double.valueOf(Double.parseDouble(text)));
            } catch (NumberFormatException e) {
                return text;
            }
        }
        if ("rating".equals(key)) {
            try {
                double rating = Double.parseDouble(text);
                if (rating <= 0) {
                    return null;
                }
                String shown = rating == Math.rint(rating) ? String.valueOf((long) rating) : String.valueOf(rating);
                return "★" + shown;
            } catch (NumberFormatException e) {
                return text;
            }
        }
        if ("session_count".equals(key) && "0".equals(text)) {
            return null;
        }
        if ("status".equals(key) || "live_service_status".equals(key)) {
            return FroglogGames.statusLabel(text);
        }
        String day = date(text);
        return day != null ? day : text;
    }

    /** {@code 2026-09-27T10:00:00Z} reads as {@code 2026-09-27}. Null when it is not a date. */
    static String date(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return FroglogGames.dateKey(value) > 0 ? value.substring(0, 10) : null;
    }

    static String label(String key) {
        String[] parts = key.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (out.length() == 0) {
                out.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase(Locale.ROOT));
            } else {
                out.append(' ').append(parts[i].toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private static boolean hidden(String key) {
        if (key.endsWith("_id") || key.endsWith("Id") || key.endsWith("_url") || key.endsWith("Url")) {
            return true;
        }
        for (int i = 0; i < HIDDEN.length; i++) {
            if (HIDDEN[i].equals(key)) {
                return true;
            }
        }
        return false;
    }
}
