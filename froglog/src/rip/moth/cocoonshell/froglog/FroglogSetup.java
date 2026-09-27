package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Marks Cocoon's onboarding as done so a test build can open the home screen.
 * Writes the same {@code ui_prefs} keys {@code MainActivity.onCreate} and
 * {@code df.r2.t} use. Disabled when {@link FroglogFlags#SKIP_SETUP} is false.
 */
public final class FroglogSetup {
    private static final String TAG = "FroglogSetup";
    private static final String UI_PREFS = "ui_prefs";
    private static final String SETUP_COMPLETED = "setup_completed";

    private FroglogSetup() {}

    public static void skipIfRequested(Context context) {
        if (!FroglogFlags.SKIP_SETUP || context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(SETUP_COMPLETED, false)) {
            return;
        }
        prefs.edit()
                .putBoolean(SETUP_COMPLETED, true)
                .putBoolean("play_setup_complete", true)
                .putBoolean("start_tutorial_on_launch", false)
                .apply();
        context.getSharedPreferences("setup_progress", Context.MODE_PRIVATE).edit().clear().apply();
        Log.i(TAG, "Marked setup_completed for the test build");
    }
}
