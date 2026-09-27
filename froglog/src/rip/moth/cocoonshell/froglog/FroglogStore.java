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

    public static String link(Context context, String key) {
        return prefs(context).getString("link_" + key, null);
    }

    public static void link(Context context, String key, long gameId, boolean live) {
        prefs(context).edit().putString("link_" + key, (live ? "live:" : "game:") + gameId).apply();
    }

    public static void decline(Context context, String key) {
        prefs(context).edit().putString("link_" + key, "no").apply();
    }

    public static boolean posted(Context context, String syncRef) {
        return syncRef != null && prefs(context).getBoolean("posted_" + syncRef, false);
    }

    public static void markPosted(Context context, String syncRef) {
        if (syncRef != null) {
            prefs(context).edit().putBoolean("posted_" + syncRef, true).apply();
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
    }

    public static synchronized void removePendingKey(Context context, String title, String platform) {
        SharedPreferences store = prefs(context);
        String next = FroglogQueue.removeKey(store.getString(PENDING, "[]"), title, platform);
        store.edit().putString(PENDING, next).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
