package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.SharedPreferences;

/** JWT only. The password is sent to Froglog and not stored. */
public final class FroglogStore {
    private static final String PREFS = "froglog_widget";
    private static final String TOKEN = "token";
    private static final String USERNAME = "username";

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

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
