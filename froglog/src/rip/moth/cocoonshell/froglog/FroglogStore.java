package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** JWT only. The password is sent to Froglog and not stored. */
public final class FroglogStore {
    private static final String PREFS = "froglog_widget";
    private static final String TOKEN = "token";
    private static final String USERNAME = "username";
    private static final String PENDING = "pending";
    /** Default {@code is_public} on sessions Cocoon posts to Froglog. */
    private static final String SESSIONS_PUBLIC = "sessions_public";

    private FroglogStore() {}

    public static void save(Context context, String token, String username) {
        prefs(context).edit().putString(TOKEN, token).putString(USERNAME, username).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static String token(Context context) {
        return prefs(context).getString(TOKEN, null);
    }

    public static String username(Context context) {
        return prefs(context).getString(USERNAME, null);
    }

    public static boolean signedIn(Context context) {
        String token = token(context);
        String username = username(context);
        return token != null && !token.isEmpty() && username != null && !username.isEmpty();
    }

    public static String filter(Context context) {
        return prefs(context).getString("filter", FroglogGames.FILTER_RECENT);
    }

    public static void setFilter(Context context, String filter) {
        prefs(context).edit().putString("filter", filter).apply();
    }

    public static boolean sessionsPublic(Context context) {
        return prefs(context).getBoolean(SESSIONS_PUBLIC, true);
    }

    public static void setSessionsPublic(Context context, boolean sessionsPublic) {
        prefs(context).edit().putBoolean(SESSIONS_PUBLIC, sessionsPublic).apply();
    }

    public static final String VISIBILITY_DEFAULT = "default";
    public static final String VISIBILITY_PUBLIC = "public";
    public static final String VISIBILITY_PRIVATE = "private";

    /**
     * Session visibility for one Froglog game, {@code game:12} or {@code live:12}. A per-game
     * "public" or "private" wins over the pod default; anything else follows the default.
     */
    public static boolean sessionsPublic(Context context, String target) {
        String choice = visibility(context, target);
        if (VISIBILITY_PUBLIC.equals(choice)) {
            return true;
        }
        if (VISIBILITY_PRIVATE.equals(choice)) {
            return false;
        }
        return sessionsPublic(context);
    }

    public static boolean sessionsPublic(Context context, FroglogGame game) {
        return game == null ? sessionsPublic(context) : sessionsPublic(context, FroglogLinks.value(game.id, game.live));
    }

    public static String visibility(Context context, String target) {
        if (target == null) {
            return VISIBILITY_DEFAULT;
        }
        return prefs(context).getString("vis_" + baseTarget(target), VISIBILITY_DEFAULT);
    }

    public static void setVisibility(Context context, String target, String choice) {
        String key = "vis_" + baseTarget(target);
        if (choice == null || VISIBILITY_DEFAULT.equals(choice)) {
            prefs(context).edit().remove(key).apply();
        } else {
            prefs(context).edit().putString(key, choice).apply();
        }
    }

    /** {@code game:12:34} (a posted session) narrows to {@code game:12}. */
    static String baseTarget(String target) {
        int first = target.indexOf(':');
        int second = first < 0 ? -1 : target.indexOf(':', first + 1);
        return second < 0 ? target : target.substring(0, second);
    }

    public static String visibilityLabel(Context context, String target) {
        String choice = visibility(context, target);
        if (VISIBILITY_PUBLIC.equals(choice)) {
            return "Always public";
        }
        if (VISIBILITY_PRIVATE.equals(choice)) {
            return "Always private";
        }
        return "Default (" + (sessionsPublic(context) ? "public" : "private") + ")";
    }

    /** default, then always public, then always private. */
    public static String nextVisibility(String choice) {
        if (VISIBILITY_PUBLIC.equals(choice)) {
            return VISIBILITY_PRIVATE;
        }
        if (VISIBILITY_PRIVATE.equals(choice)) {
            return VISIBILITY_DEFAULT;
        }
        return VISIBILITY_PUBLIC;
    }

    public static String link(Context context, String key) {
        return prefs(context).getString(FroglogLinks.LINK + key, null);
    }

    /** Links a Cocoon title and keeps its readable name for the mappings screen. */
    public static void link(Context context, String title, String platform, long gameId, boolean live) {
        String key = FroglogMatch.linkKey(title, platform);
        prefs(context).edit()
                .putString(FroglogLinks.LINK + key, FroglogLinks.value(gameId, live))
                .putString(FroglogLinks.NAME + key, FroglogLinks.encodeName(title, platform))
                .apply();
    }

    /** Forgets a link or a decline, so the next session for that title is matched again. */
    public static void unlink(Context context, String key) {
        prefs(context).edit().remove(FroglogLinks.LINK + key).remove(FroglogLinks.NAME + key).commit();
    }

    public static List<FroglogLinks.Mapping> mappings(Context context, java.util.Map<String, String[]> fallback) {
        return FroglogLinks.read(prefs(context).getAll(), fallback);
    }

    /** Every {@code game:12} or {@code live:12} a Cocoon title has been linked to. */
    public static java.util.Set<String> linkedGames(Context context) {
        java.util.TreeSet<String> out = new java.util.TreeSet<String>();
        for (java.util.Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            Object value = entry.getValue();
            if (entry.getKey().startsWith("link_") && value instanceof String
                    && (((String) value).startsWith("game:") || ((String) value).startsWith("live:"))) {
                out.add((String) value);
            }
        }
        return out;
    }

    public static boolean repaired(Context context, String game) {
        return prefs(context).getBoolean("repaired_v1_" + game, false);
    }

    public static void markRepaired(Context context, String game) {
        prefs(context).edit().putBoolean("repaired_v1_" + game, true).apply();
    }

    public static void decline(Context context, String title, String platform) {
        String key = FroglogMatch.linkKey(title, platform);
        prefs(context).edit()
                .putString(FroglogLinks.LINK + key, FroglogLinks.DECLINED)
                .putString(FroglogLinks.NAME + key, FroglogLinks.encodeName(title, platform))
                .apply();
    }

    /** "game:12", "live:4", or null for a Cocoon title that is unmapped or declined. */
    public static String mappedTarget(Context context, String title, String platform) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        String value = link(context, FroglogMatch.linkKey(title, platform == null ? "" : platform));
        if (value == null || FroglogLinks.DECLINED.equals(value)) {
            return null;
        }
        return value.startsWith("game:") || value.startsWith("live:") ? value : null;
    }

    public static boolean declined(Context context, String title, String platform) {
        return FroglogLinks.DECLINED.equals(
                link(context, FroglogMatch.linkKey(title, platform == null ? "" : platform)));
    }

    public static boolean shotUploaded(Context context, String uri) {
        return uri != null && prefs(context).getBoolean("shot_" + uri.hashCode() + "_" + uri.length(), false);
    }

    public static void markShotUploaded(Context context, String uri) {
        if (uri != null) {
            prefs(context).edit().putBoolean("shot_" + uri.hashCode() + "_" + uri.length(), true).apply();
        }
    }

    public static boolean posted(Context context, String syncRef) {
        return syncRef != null && prefs(context).getBoolean("posted_" + syncRef, false);
    }

    public static void markPosted(Context context, String syncRef) {
        if (syncRef != null) {
            prefs(context).edit().putBoolean("posted_" + syncRef, true).apply();
        }
    }

    /** Also remembers the minutes sent and the Froglog row, so a longer Cocoon session can update it. */
    public static void markPosted(Context context, String syncRef, int minutes, String remote) {
        if (syncRef == null) {
            return;
        }
        SharedPreferences.Editor edit = prefs(context).edit()
                .putBoolean("posted_" + syncRef, true)
                .putInt("posted_min_" + syncRef, minutes);
        if (remote != null) {
            edit.putString("remote_" + syncRef, remote);
        }
        edit.commit();
    }

    /** Minutes already on Froglog for this Cocoon session. Unknown for older posts, so no update is tried. */
    public static int postedMinutes(Context context, String syncRef) {
        return syncRef == null ? Integer.MAX_VALUE : prefs(context).getInt("posted_min_" + syncRef, Integer.MAX_VALUE);
    }

    public static String remote(Context context, String syncRef) {
        return syncRef == null ? null : prefs(context).getString("remote_" + syncRef, null);
    }

    /** End time of the newest Cocoon session already copied into the queue. 0 before the first scan. */
    public static long sessionsSince(Context context) {
        return prefs(context).getLong("sessions_since", 0L);
    }

    public static void setSessionsSince(Context context, long endTimeMs) {
        prefs(context).edit().putLong("sessions_since", endTimeMs).commit();
    }

    /** True while Froglog may still show this user in game, including after a failed clear. */
    public static boolean presenceOn(Context context) {
        return prefs(context).getBoolean("presence_on", false);
    }

    public static void setPresenceOn(Context context, boolean on) {
        prefs(context).edit().putBoolean("presence_on", on).commit();
    }

    public static boolean asked(Context context, String sync) {
        return sync != null && prefs(context).getBoolean("asked_" + sync, false);
    }

    public static void markAsked(Context context, String sync) {
        if (sync != null) {
            prefs(context).edit().putBoolean("asked_" + sync, true).apply();
        }
    }

    public static synchronized List<FroglogQueue.Item> pending(Context context) {
        return FroglogQueue.parse(prefs(context).getString(PENDING, "[]"));
    }

    public static synchronized boolean hasPending(Context context, String sync) {
        return FroglogQueue.contains(prefs(context).getString(PENDING, "[]"), sync);
    }

    public static synchronized void enqueuePending(Context context, String title, String platform,
            int minutes, String date, String sync) {
        SharedPreferences store = prefs(context);
        String next = FroglogQueue.upsert(store.getString(PENDING, "[]"), title, platform, minutes, date, sync);
        store.edit().putString(PENDING, next).commit();
        FroglogRecentWidget.refresh(context);
    }

    public static synchronized void pendingError(Context context, String sync, String error) {
        SharedPreferences store = prefs(context);
        String next = FroglogQueue.rememberError(store.getString(PENDING, "[]"), sync, error);
        store.edit().putString(PENDING, next).commit();
    }

    public static synchronized void removePending(Context context, String sync) {
        SharedPreferences store = prefs(context);
        String next = FroglogQueue.remove(store.getString(PENDING, "[]"), sync);
        store.edit().putString(PENDING, next).commit();
        FroglogRecentWidget.refresh(context);
    }

    public static synchronized void removePendingKey(Context context, String title, String platform) {
        SharedPreferences store = prefs(context);
        String next = FroglogQueue.removeKey(store.getString(PENDING, "[]"), title, platform);
        store.edit().putString(PENDING, next).commit();
        FroglogRecentWidget.refresh(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
