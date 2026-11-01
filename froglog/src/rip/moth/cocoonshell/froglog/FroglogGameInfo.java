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
            {"dev|developer|developers", "Developer"},
            {"studio_country|dev_country", "Country"},
            {"publisher|publishers", "Publisher"},
            {"rel_date|release_date|release_year|year", "Released"},
            {"screenshot_count", "Screenshots"},
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
            "dnf", "is_public", "private", "game_type", "description", "rel_date_category", "igdb_slug",
    };

    /** The game's catalog description, or null. Shown as its own block rather than a row. */
    public static String description(JSONObject game) {
        if (game == null || game.isNull("description")) {
            return null;
        }
        String text = game.optString("description", "").trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    /** One row of the details table. {@code editor} is status, date, number, bool, text, or readonly. */
    public static final class Line {
        public final String key;
        public final String label;
        public final String value;
        public final String editor;

        public Line(String key, String label, String value, String editor) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.editor = editor;
        }
    }

    public static List<String[]> rows(JSONObject game) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        List<Line> lines = lines(game);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            out.add(new String[] {line.label, line.value});
        }
        return out;
    }

    public static List<Line> lines(JSONObject game) {
        ArrayList<Line> out = new ArrayList<Line>();
        if (game == null) {
            return out;
        }
        TreeSet<String> used = new TreeSet<String>();
        boolean sawStatus = false;
        for (int i = 0; i < KNOWN.length; i++) {
            String[] keys = KNOWN[i][0].split("\\|");
            for (int k = 0; k < keys.length; k++) {
                used.add(keys[k]);
            }
            for (int k = 0; k < keys.length; k++) {
                Object raw = game.opt(keys[k]);
                String shown = value(keys[k], raw);
                if (shown != null) {
                    String label = KNOWN[i][1];
                    String key = "Hours played".equals(label) ? "hours_played" : keys[k];
                    if ("Status".equals(label)) {
                        sawStatus = true;
                    }
                    out.add(new Line(key, label, shown, editorFor(label, raw, shown)));
                    break;
                }
            }
        }
        if (!sawStatus) {
            String derived = derivedStatus(game, game.has("live_service_status") && !game.has("status"));
            if (derived != null && !derived.isEmpty()) {
                out.add(0, new Line("status", "Status", derived, "status"));
            }
        }
        if (game.optBoolean("dnf", false) && !"DNF".equalsIgnoreCase(game.optString("status", ""))) {
            out.add(new Line("dnf", "Did not finish", "Yes", "bool"));
            used.add("dnf");
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
            Object raw = game.opt(key);
            String shown = value(key, raw);
            if (shown != null) {
                out.add(new Line(key, label(key), shown, editorFor(label(key), raw, shown)));
            }
        }
        return out;
    }

    public static String[] choices(String editor, boolean live) {
        if ("status".equals(editor)) {
            return live
                    ? new String[] {"Live", "Dormant"}
                    : new String[] {"In Progress", "Completed", "DNF", "Dormant", "Imported"};
        }
        if ("bool".equals(editor)) {
            return new String[] {"Yes", "No"};
        }
        return new String[0];
    }

    /**
     * Writes one edited table value back onto the game object Froglog accepts.
     * Returns an error for a value that does not fit the field, or null.
     */
    public static String apply(JSONObject game, boolean live, String key, String label, String editor, String entered) {
        if (game == null || key == null || "readonly".equals(editor)) {
            return null;
        }
        String text = entered == null ? "" : entered.trim();
        try {
            if ("status".equals(editor)) {
                applyStatus(game, live, text);
                return null;
            }
            if ("bool".equals(editor)) {
                game.put(key, "Yes".equalsIgnoreCase(text));
                return null;
            }
            if ("date".equals(editor)) {
                if (text.isEmpty()) {
                    game.put(key, JSONObject.NULL);
                    return null;
                }
                if (date(text) == null) {
                    return label + " needs a date like 2026-09-01";
                }
                game.put(key, text.substring(0, 10));
                return null;
            }
            if ("number".equals(editor)) {
                if (text.isEmpty()) {
                    game.put("Rating".equals(label) ? "rating" : "hours_played", 0);
                    return null;
                }
                String numeric = text.replace("★", "").replace("h", "").replace("H", "").trim();
                double number = Double.parseDouble(numeric);
                if ("Rating".equals(label)) {
                    double stored = game.optDouble("rating", 0);
                    game.put("rating", stored > 5 ? Math.round(number * 20.0) : number);
                } else {
                    game.put("hours_played", number);
                }
                return null;
            }
            if (text.isEmpty()) {
                game.put(key, JSONObject.NULL);
            } else {
                game.put(key, text);
            }
            return null;
        } catch (NumberFormatException e) {
            return label + " needs a number";
        } catch (Exception e) {
            return "Could not save " + label;
        }
    }

    /** Status Froglog shows, including one derived from dates when the row has no status field. */
    public static String derivedStatus(JSONObject game, boolean live) {
        if (game == null) {
            return "";
        }
        if (live) {
            return "dormant".equalsIgnoreCase(game.optString("live_service_status", "")) ? "Dormant" : "Live";
        }
        String override = game.isNull("status_override") ? "" : game.optString("status_override", "");
        if ("dormant".equalsIgnoreCase(override)) {
            return "Dormant";
        }
        if ("completed".equalsIgnoreCase(override)) {
            return "Completed";
        }
        if (game.optBoolean("dnf", false)) {
            return "DNF";
        }
        if (date(game.optString("end_date", "")) != null) {
            return "Completed";
        }
        if (date(game.optString("start_date", "")) != null) {
            return "In Progress";
        }
        if ("Imported".equalsIgnoreCase(game.optString("status", ""))) {
            return "Imported";
        }
        String status = game.isNull("status") ? "" : game.optString("status", "").trim();
        if (status.isEmpty() || "null".equals(status)) {
            return "";
        }
        String label = FroglogGames.statusLabel(status);
        return label == null ? "" : label;
    }

    private static void applyStatus(JSONObject game, boolean live, String choice) throws Exception {
        if (live) {
            game.put("live_service_status", "Dormant".equalsIgnoreCase(choice) ? "dormant" : "active");
            return;
        }
        if ("Completed".equalsIgnoreCase(choice)) {
            game.put("status_override", "completed");
            game.put("dnf", false);
            return;
        }
        if ("DNF".equalsIgnoreCase(choice)) {
            game.put("dnf", true);
            game.put("status_override", JSONObject.NULL);
            return;
        }
        if ("Dormant".equalsIgnoreCase(choice)) {
            game.put("status_override", "dormant");
            game.put("dnf", false);
            return;
        }
        if ("Imported".equalsIgnoreCase(choice)) {
            game.put("status", "Imported");
            game.put("status_override", JSONObject.NULL);
            game.put("dnf", false);
            game.put("start_date", JSONObject.NULL);
            game.put("end_date", JSONObject.NULL);
            return;
        }
        game.put("status_override", JSONObject.NULL);
        game.put("dnf", false);
        game.put("end_date", JSONObject.NULL);
        if (date(game.optString("start_date", "")) == null) {
            game.put("start_date", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(new java.util.Date()));
        }
    }

    private static String editorFor(String label, Object raw, String shown) {
        if (raw instanceof Boolean) {
            return "bool";
        }
        if ("Status".equals(label)) {
            return "status";
        }
        if ("Sessions".equals(label) || "Screenshots".equals(label)) {
            return "readonly";
        }
        if ("Hours played".equals(label) || "Rating".equals(label)) {
            return "number";
        }
        if ("Started".equals(label) || "Finished".equals(label) || "Last played".equals(label)
                || "Released".equals(label) || "Added".equals(label)) {
            return "date";
        }
        if (shown != null && date(shown) != null && shown.length() >= 10 && shown.charAt(4) == '-') {
            return "date";
        }
        return "text";
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
                // Froglog stores 0-100, 20 per star; older rows may already be stars.
                if (rating > 5) {
                    rating = Math.round(rating / 2.0) / 10.0;
                }
                String shown = rating == Math.rint(rating) ? String.valueOf((long) rating) : String.valueOf(rating);
                return "★" + shown;
            } catch (NumberFormatException e) {
                return text;
            }
        }
        if (("session_count".equals(key) || "screenshot_count".equals(key)) && "0".equals(text)) {
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
