package rip.moth.cocoonshell.froglog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cocoon sessions that still need a Froglog game. One row per play, keyed by the Cocoon
 * session id, so a retry posts the same hours instead of splitting them.
 */
public final class FroglogQueue {
    public static final int MAX = 40;

    private FroglogQueue() {}

    public static final class Item {
        public final String title;
        public final String platform;
        public final int minutes;
        public final String date;
        public final String sync;
        public final String error;

        public Item(String title, String platform, int minutes, String date, String sync, String error) {
            this.title = title == null ? "" : title;
            this.platform = platform == null ? "" : platform;
            this.minutes = minutes;
            this.date = date == null ? "" : date;
            this.sync = sync == null ? "" : sync;
            this.error = error == null ? "" : error;
        }
    }

    public static List<Item> parse(String json) {
        ArrayList<Item> items = new ArrayList<Item>();
        if (json == null || json.trim().isEmpty()) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String sync = obj.optString("sync", "");
                if (sync.isEmpty()) {
                    continue;
                }
                items.add(new Item(
                        obj.optString("title", ""),
                        obj.optString("platform", ""),
                        obj.optInt("minutes", 0),
                        obj.optString("date", ""),
                        sync,
                        obj.optString("error", "")));
            }
        } catch (Exception ignored) {
            return new ArrayList<Item>();
        }
        return items;
    }

    public static String upsert(String json, String title, String platform, int minutes, String date, String sync) {
        if (sync == null || sync.isEmpty()) {
            return json == null ? "[]" : json;
        }
        ArrayList<Item> items = new ArrayList<Item>(parse(json));
        String keptError = "";
        for (int i = 0; i < items.size(); i++) {
            if (sync.equals(items.get(i).sync)) {
                keptError = items.get(i).error;
                items.remove(i);
                break;
            }
        }
        items.add(0, new Item(title, platform, minutes, date, sync, keptError));
        while (items.size() > MAX) {
            items.remove(items.size() - 1);
        }
        return write(items);
    }

    public static String rememberError(String json, String sync, String error) {
        ArrayList<Item> items = new ArrayList<Item>(parse(json));
        String message = error == null ? "" : error;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (sync != null && sync.equals(item.sync)) {
                items.set(i, new Item(item.title, item.platform, item.minutes, item.date, item.sync, message));
            }
        }
        return write(items);
    }

    public static String remove(String json, String sync) {
        ArrayList<Item> items = new ArrayList<Item>(parse(json));
        for (int i = items.size() - 1; i >= 0; i--) {
            if (sync != null && sync.equals(items.get(i).sync)) {
                items.remove(i);
            }
        }
        return write(items);
    }

    public static String removeKey(String json, String title, String platform) {
        String key = FroglogMatch.linkKey(title, platform);
        ArrayList<Item> items = new ArrayList<Item>(parse(json));
        for (int i = items.size() - 1; i >= 0; i--) {
            Item item = items.get(i);
            if (key.equals(FroglogMatch.linkKey(item.title, item.platform))) {
                items.remove(i);
            }
        }
        return write(items);
    }

    public static List<Item> matching(List<Item> items, String title, String platform) {
        String key = FroglogMatch.linkKey(title, platform);
        ArrayList<Item> kept = new ArrayList<Item>();
        if (items == null) {
            return kept;
        }
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (key.equals(FroglogMatch.linkKey(item.title, item.platform))) {
                kept.add(item);
            }
        }
        return kept;
    }

    public static boolean contains(String json, String sync) {
        if (sync == null || sync.isEmpty()) {
            return false;
        }
        List<Item> items = parse(json);
        for (int i = 0; i < items.size(); i++) {
            if (sync.equals(items.get(i).sync)) {
                return true;
            }
        }
        return false;
    }

    private static String write(List<Item> items) {
        JSONArray array = new JSONArray();
        List<Item> safe = items == null ? Collections.<Item>emptyList() : items;
        for (int i = 0; i < safe.size(); i++) {
            Item item = safe.get(i);
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", item.title);
                obj.put("platform", item.platform);
                obj.put("minutes", item.minutes);
                obj.put("date", item.date);
                obj.put("sync", item.sync);
                obj.put("error", item.error);
                array.put(obj);
            } catch (Exception ignored) {
                // A single bad row is dropped rather than failing the whole queue.
            }
        }
        return array.toString();
    }
}
