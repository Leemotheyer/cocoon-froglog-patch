package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.util.Log;

import rip.moth.cocoonshell.data.model.GameSession;

/**
 * Called when Cocoon inserts one finished play session. The row is already in
 * {@code game_sessions}, so this only wakes the sync and clears Online Now.
 */
public final class FroglogSessionBridge {
    private static final String TAG = "FroglogWidget";

    private FroglogSessionBridge() {}

    public static void onInserted(GameSession session) {
        try {
            Context context = CatalogHook.context;
            if (context == null || session == null || !FroglogStore.signedIn(context)) {
                return;
            }
            FroglogPresence.ended(context);
            FroglogSync.kick(context);
        } catch (Throwable t) {
            Log.e(TAG, "Session hook failed", t);
        }
    }
}
