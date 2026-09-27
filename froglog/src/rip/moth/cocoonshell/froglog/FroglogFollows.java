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
        return people(activityJson, onlineJson, null, self);
    }

    /**
     * {@code followingJson} is {@code GET /users/me/following}. {@code /activity/online} lists
     * everyone in game, so an online row only counts for someone the viewer follows. Without
     * the follow list, usernames from the activity feed stand in for it.
     */
    public static List<FroglogFollow> people(String activityJson, String onlineJson, String followingJson, String self) {
        JSONArray activity = activityArray(activityJson);
        Map<String, JSONObject> online = onlineByUser(onlineJson);
        Map<String, JSONObject> following = followingByUser(followingJson);
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
            if (following != null && !following.containsKey(key)) {
                continue;
            }
            JSONObject previous = latest.get(key);
            if (previous == null || newer(item, previous)) {
                latest.put(key, item);
            }
        }
        LinkedHashMap<String, JSONObject> followed = new LinkedHashMap<String, JSONObject>(latest);
        if (following != null) {
            for (Map.Entry<String, JSONObject> entry : following.entrySet()) {
                if (!followed.containsKey(entry.getKey()) && !sameUser(entry.getKey(), self)) {
                    followed.put(entry.getKey(), entry.getValue());
                }
            }
        }
        ArrayList<FroglogFollow> playing = new ArrayList<FroglogFollow>();
        ArrayList<FroglogFollow> rest = new ArrayList<FroglogFollow>();
        for (Map.Entry<String, JSONObject> entry : followed.entrySet()) {
            FroglogFollow person = person(entry.getValue(), online.get(entry.getKey()));
            (person.playing ? playing : rest).add(person);
        }
        ArrayList<FroglogFollow> people = new ArrayList<FroglogFollow>(playing);
        for (int i = 0; i < rest.size() && people.size() < 30; i++) {
            people.add(rest.get(i));
        }
        return people;
    }

    /** Null when the follow list could not be read, so callers fall back to the activity feed. */
    private static Map<String, JSONObject> followingByUser(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            String trimmed = json.trim();
            JSONArray array = null;
            if (trimmed.startsWith("[")) {
                array = new JSONArray(trimmed);
            } else {
                JSONObject object = new JSONObject(trimmed);
                String[] keys = {"following", "users", "follows", "data"};
                for (int i = 0; i < keys.length && array == null; i++) {
                    array = object.optJSONArray(keys[i]);
                }
            }
            if (array == null) {
                return null;
            }
            LinkedHashMap<String, JSONObject> map = new LinkedHashMap<String, JSONObject>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject user = item.optJSONObject("user");
                if (user != null) {
                    item = user;
                }
                String username = firstText(item, "target_username", "username", "following_username");
                if (username != null) {
                    JSONObject row = new JSONObject(item.toString());
                    row.put("username", username);
                    map.put(username.toLowerCase(Locale.ROOT), row);
                }
            }
            return map;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static FroglogFollow person(JSONObject item, JSONObject live) {
        String username = item.optString("username", "").trim();
        String name = firstText(item, "nickname", "display_username", "displayUsername");
        if (name == null) {
            name = username;
        }
        String avatar = firstText(item, "avatar_url", "avatarUrl");
        String game = firstText(item, "game_title", "gameTitle");
        boolean playing = live != null;
        String status;
        if (playing) {
            // The feed's game_title is last-played. Only the online row names the game open now.
            game = liveTitle(live);
            String liveAvatar = firstText(live, "avatarUrl", "avatar_url");
            if (avatar == null) {
                avatar = liveAvatar;
            }
            String liveName = firstText(live, "displayUsername", "display_username", "nickname");
            if (liveName != null) {
                name = liveName;
            }
            status = game == null ? "In game" : "Playing " + game;
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

    /**
     * Froglog's Online Now card is in-game only, so every {@code /activity/online} row is
     * someone playing, with or without a title. Activity-feed rows are last-seen, never presence.
     */
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
        return true;
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
