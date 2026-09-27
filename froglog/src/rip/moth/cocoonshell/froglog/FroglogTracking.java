package rip.moth.cocoonshell.froglog;

import org.json.JSONObject;

import java.util.List;

/**
 * Prepares a regular Froglog game before a session is posted.
 * A PUT echoes the GET body. A partial body can wipe sessions, so this only returns a payload
 * when something actually has to change.
 */
public final class FroglogTracking {
    private FroglogTracking() {}

    public static JSONObject preparePayload(JSONObject game, String sessionDate) throws Exception {
        if (game == null) {
            return null;
        }
        JSONObject obj = new JSONObject(game.toString());
        obj.remove("total_hours");
        obj.remove("session_count");
        obj.remove("last_session_date");
        obj.remove("initial_session_hours");
        boolean changed = false;
        String status = obj.optString("status", "");
        if ("Completed".equals(status) || "DNF".equals(status)) {
            obj.put("dnf", false);
            obj.put("end_date", JSONObject.NULL);
            obj.put("status_override", JSONObject.NULL);
            changed = true;
        }
        if (blank(obj, "start_date")) {
            String date = sessionDate != null && sessionDate.length() >= 10 ? sessionDate.substring(0, 10) : "";
            if (!date.isEmpty()) {
                obj.put("start_date", date);
                changed = true;
            }
        }
        if (!trackingOn(obj)) {
            double hours = hoursPlayed(obj);
            obj.put("session_tracking", true);
            obj.put("sessions_public", true);
            if (hours > 0) {
                obj.put("initial_session_hours", hours);
            }
            changed = true;
        }
        return changed ? obj : null;
    }

    /**
     * Minutes to post for a finished play. Cocoon's duration field is often 0 when
     * Now Playing fired but Track playtime did not, so elapsed time is the fallback.
     * A play that lasted at least one presence poll (15s) counts as one minute.
     */
    public static int playMinutes(int reported, long startTimeMs, long endTimeMs) {
        if (reported >= 1) {
            return reported;
        }
        if (startTimeMs <= 0 || endTimeMs <= startTimeMs) {
            return 0;
        }
        long elapsed = endTimeMs - startTimeMs;
        if (elapsed < 15L * 1000L) {
            return 0;
        }
        int minutes = (int) ((elapsed + 59999L) / 60000L);
        return minutes < 1 ? 1 : minutes;
    }

    /** The live-service id when exactly one row shares the title. Two matches is not a match. */
    public static Long uniqueLiveId(List<FroglogGame> live, String title) {
        String wanted = FroglogMatch.normalizeTitle(title);
        if (wanted.isEmpty() || live == null) {
            return null;
        }
        Long found = null;
        for (int i = 0; i < live.size(); i++) {
            FroglogGame game = live.get(i);
            if (game == null || !game.live) {
                continue;
            }
            if (!wanted.equals(FroglogMatch.normalizeTitle(game.title))) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = Long.valueOf(game.id);
        }
        return found;
    }

    private static boolean trackingOn(JSONObject obj) {
        Object raw = obj.opt("session_tracking");
        if (raw instanceof Boolean) {
            return ((Boolean) raw).booleanValue();
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            return "true".equalsIgnoreCase((String) raw) || "1".equals(raw);
        }
        return false;
    }

    private static double hoursPlayed(JSONObject obj) {
        if (!obj.has("hours_played") || obj.isNull("hours_played")) {
            return 0;
        }
        Object raw = obj.opt("hours_played");
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean blank(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return true;
        }
        String value = obj.optString(key, "");
        return value.isEmpty() || "null".equals(value);
    }
}
