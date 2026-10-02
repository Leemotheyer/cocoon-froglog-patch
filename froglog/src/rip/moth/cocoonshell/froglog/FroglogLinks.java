package rip.moth.cocoonshell.froglog;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The Cocoon-title to Froglog-game mappings kept in the widget prefs. A link key is normalized,
 * so the readable Cocoon title and platform are kept beside it under {@code linkname_}.
 */
public final class FroglogLinks {
    public static final String LINK = "link_";
    public static final String NAME = "linkname_";
    public static final String DECLINED = "no";

    private FroglogLinks() {}

    public static final class Mapping {
        public final String key;
        public final String title;
        public final String platform;
        public final long gameId;
        public final boolean live;
        public final boolean declined;

        Mapping(String key, String title, String platform, long gameId, boolean live, boolean declined) {
            this.key = key;
            this.title = title;
            this.platform = platform;
            this.gameId = gameId;
            this.live = live;
            this.declined = declined;
        }
    }

    public static String value(long gameId, boolean live) {
        return (live ? "live:" : "game:") + gameId;
    }

    public static String encodeName(String title, String platform) {
        JSONArray out = new JSONArray();
        out.put(title == null ? "" : title.trim());
        out.put(platform == null ? "" : platform.trim());
        return out.toString();
    }

    /** {title, platform}, or null when the stored name is missing or unreadable. */
    public static String[] decodeName(Object raw) {
        if (!(raw instanceof String)) {
            return null;
        }
        try {
            JSONArray array = new JSONArray((String) raw);
            String title = array.optString(0, "");
            return title.isEmpty() ? null : new String[] {title, array.optString(1, "")};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Every stored mapping, mapped games first, each group by Cocoon title. {@code fallback} gives
     * a readable {title, platform} for links saved before names were kept, by link key.
     */
    public static List<Mapping> read(Map<String, ?> prefs, Map<String, String[]> fallback) {
        ArrayList<Mapping> out = new ArrayList<Mapping>();
        for (Map.Entry<String, ?> entry : prefs.entrySet()) {
            if (!entry.getKey().startsWith(LINK) || !(entry.getValue() instanceof String)) {
                continue;
            }
            String key = entry.getKey().substring(LINK.length());
            String value = (String) entry.getValue();
            boolean declined = DECLINED.equals(value);
            boolean live = value.startsWith("live:");
            long id = 0;
            if (!declined) {
                if (!live && !value.startsWith("game:")) {
                    continue;
                }
                try {
                    id = Long.parseLong(value.substring(value.indexOf(':') + 1));
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            String[] name = decodeName(prefs.get(NAME + key));
            if (name == null && fallback != null) {
                name = fallback.get(key);
            }
            String title;
            String platform;
            if (name != null) {
                title = name[0];
                platform = name[1];
            } else {
                int bar = key.lastIndexOf('|');
                title = bar < 0 ? key : key.substring(0, bar);
                platform = bar < 0 ? "" : key.substring(bar + 1);
            }
            out.add(new Mapping(key, title, platform, id, live, declined));
        }
        Collections.sort(out, new Comparator<Mapping>() {
            @Override
            public int compare(Mapping a, Mapping b) {
                if (a.declined != b.declined) {
                    return a.declined ? 1 : -1;
                }
                int byTitle = a.title.compareToIgnoreCase(b.title);
                return byTitle != 0 ? byTitle : a.platform.compareToIgnoreCase(b.platform);
            }
        });
        return out;
    }

    /** The library row a mapping points at, or null when it is no longer in the library. */
    public static FroglogGame find(List<FroglogGame> library, Mapping mapping) {
        if (library == null || mapping == null || mapping.declined) {
            return null;
        }
        for (int i = 0; i < library.size(); i++) {
            FroglogGame game = library.get(i);
            if (game != null && game.id == mapping.gameId && game.live == mapping.live) {
                return game;
            }
        }
        return null;
    }
}
