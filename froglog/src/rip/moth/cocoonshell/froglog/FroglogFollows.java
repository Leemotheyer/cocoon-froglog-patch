package rip.moth.cocoonshell.froglog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns {@code GET /api/activity} into the people the viewer follows.
 * Last-played rows stay in this list for the Froglog pod. Only a live
 * now-playing row from {@code /activity/online} sets {@code playing}.
 */
public final class FroglogFollows {
    private FroglogFollows() {}

    public static List<FroglogFollow> people(String activityJson, String onlineJson, String self) {
        JSONArray activity = activityArray(activityJson);
        Map<String, JSONObject> online = onlineByUser(onlineJson);
        LinkedHashMap<String, JSONObject> latest = new LinkedHashMap<String, JSONObject>();
        for (int i = 0; i < activity.length(); i++) {
            JSONObject item = activity.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String username = item.optString("username", "").trim();
            if (username.isEmpty() || sameUser(username, self)) {
                continue;
            }
            String key = username.toLowerCase(Locale.ROOT);
            JSONObject previous = latest.get(key);
            if (previous == null || newer(item, previous)) {
                latest.put(key, item);
            }
        }
        ArrayList<FroglogFollow> people = new ArrayList<FroglogFollow>();
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
        for (JSONObject item : latest.values()) {
            String username = item.optString("username", "").trim();
            String key = username.toLowerCase(Locale.ROOT);
            JSONObject live = online.get(key);
            people.add(person(item, live));
            seen.put(key, Boolean.TRUE);
            if (people.size() >= 12) {
                return people;
            }
        }
        for (Map.Entry<String, JSONObject> entry : online.entrySet()) {
            if (seen.containsKey(entry.getKey()) || sameUser(entry.getKey(), self)) {
                continue;
            }
            people.add(person(entry.getValue(), entry.getValue()));
            if (people.size() >= 12) {
                break;
            }
        }
        return people;
    }

    private static FroglogFollow person(JSONObject item, JSONObject live) {
        String username = item.optString("username", "").trim();
        String name = firstText(item, "nickname", "display_username", "displayUsername");
        if (name == null) {
            name = username;
        }
        String avatar = firstText(item, "avatar_url", "avatarUrl");
        String game = firstText(item, "game_title", "gameTitle");
        boolean playing = nowPlaying(live);
        String status;
        if (playing) {
            String liveTitle = liveTitle(live);
            if (liveTitle != null) {
                game = liveTitle;
            }
            String liveAvatar = firstText(live, "avatarUrl", "avatar_url");
            if (avatar == null) {
                avatar = liveAvatar;
            }
            String liveName = firstText(live, "displayUsername", "display_username", "nickname");
            if (liveName != null) {
                name = liveName;
            }
            status = game == null ? "On Froglog" : "Playing " + game;
        } else if (game != null) {
            status = label(item.optString("type", "")) + " · " + game;
        } else {
            status = label(item.optString("type", ""));
        }
        return new FroglogFollow(username, name, avatar, status, game, playing);
    }

    static String label(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "Froglog";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if ("session_logged".equals(normalized) || "session".equals(normalized)) {
            return "Logged";
        }
        if ("game_completed".equals(normalized) || "completed".equals(normalized)) {
            return "Finished";
        }
        if ("game_started".equals(normalized) || "started".equals(normalized)) {
            return "Started";
        }
        String[] parts = normalized.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.length() == 0 ? "Froglog" : out.toString();
    }

    private static JSONArray activityArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new JSONArray();
        }
        try {
            JSONObject object = new JSONObject(json);
            JSONArray activity = object.optJSONArray("activity");
            return activity == null ? new JSONArray() : activity;
        } catch (Exception ignored) {
            try {
                return new JSONArray(json);
            } catch (Exception again) {
                return new JSONArray();
            }
        }
    }

    private static Map<String, JSONObject> onlineByUser(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            JSONArray array = onlineArray(json);
            LinkedHashMap<String, JSONObject> map = new LinkedHashMap<String, JSONObject>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String username = item.optString("username", "").trim();
                if (!username.isEmpty() && nowPlaying(item)) {
                    map.put(username.toLowerCase(Locale.ROOT), item);
                }
            }
            return map;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static JSONArray onlineArray(String json) throws Exception {
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }
        JSONObject object = new JSONObject(trimmed);
        String[] keys = {"online", "users", "people", "now_playing", "nowPlaying"};
        for (int i = 0; i < keys.length; i++) {
            JSONArray array = object.optJSONArray(keys[i]);
            if (array != null) {
                return array;
            }
        }
        return new JSONArray();
    }

    /** Froglog's Online Now card is in-game only. Last-seen activity is not presence. */
    static boolean nowPlaying(JSONObject live) {
        if (live == null) {
            return false;
        }
        String type = live.optString("type", "").trim().toLowerCase(Locale.ROOT);
        if ("session_logged".equals(type) || "session".equals(type)
                || "game_completed".equals(type) || "completed".equals(type)
                || "game_started".equals(type) || "started".equals(type)) {
            return false;
        }
        return liveTitle(live) != null
                || firstText(live, "started_at", "startedAt") != null
                || live.has("game_id") && !live.isNull("game_id");
    }

    private static String liveTitle(JSONObject live) {
        return firstText(live, "title", "now_playing", "nowPlaying");
    }

    private static boolean newer(JSONObject item, JSONObject previous) {
        return item.optString("created_at", "").compareTo(previous.optString("created_at", "")) > 0;
    }

    private static boolean sameUser(String username, String self) {
        return self != null && username.equalsIgnoreCase(self.trim());
    }

    private static String firstText(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equals(value)) {
                return value;
            }
        }
        return null;
    }
}
