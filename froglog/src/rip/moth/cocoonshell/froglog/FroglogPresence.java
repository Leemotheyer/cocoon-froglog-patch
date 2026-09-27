package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.util.Log;

/**
 * Reports the open Cocoon session to Froglog the same way LilyPad does:
 * {@code PUT /users/me/now-playing} while a game is running, then DELETE when it ends.
 */
public final class FroglogPresence {
    private static final String TAG = "FroglogWidget";
    private static final long POLL_MS = 15L * 1000L;
    private static final long HEARTBEAT_MS = 2L * 60L * 1000L;
    private static final long LIBRARY_MS = 5L * 60L * 1000L;

    private static final Object LOCK = new Object();
    private static boolean started;
    private static String postedKey;
    private static long postedAt;
    private static boolean visibilityOn;
    private static FroglogClient.Recent library;
    private static long libraryAt;
    private static CocoonLibrary.Playing lastPlaying;
    private static Target lastTarget;

    private FroglogPresence() {}

    public static void start(Context context) {
        synchronized (LOCK) {
            if (started || context == null) {
                return;
            }
            started = true;
        }
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        sync(app);
                    } catch (Throwable t) {
                        Log.e(TAG, "Froglog presence poll failed", t);
                    }
                    try {
                        Thread.sleep(POLL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "froglog-presence").start();
    }

    /** The insert path will post this play, so the presence-end backup must not. */
    public static void skipCloseLog(Context context) {
        CocoonLibrary.Playing playing;
        synchronized (LOCK) {
            playing = lastPlaying;
        }
        if (context != null && playing != null) {
            FroglogStore.markPosted(context, playing.title + ":" + playing.startTimeMs);
        }
    }

    /** The finished session has been written, so Online Now should clear now. */
    public static void ended(Context context) {
        if (context == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                clear(app, true);
            }
        }, "froglog-presence-clear").start();
    }

    static void sync(Context context) {
        if (!FroglogStore.signedIn(context)) {
            clear(context, false);
            return;
        }
        CocoonLibrary.Playing playing = CocoonLibrary.playing(context);
        if (playing == null) {
            clear(context, false);
            return;
        }
        Target target = resolve(context, playing);
        if (target == null) {
            clear(context, false);
            return;
        }
        String key = FroglogNowPlaying.key(target.id, target.live, playing.startTimeMs);
        long now = System.currentTimeMillis();
        String token = FroglogStore.token(context);
        boolean needPresence;
        synchronized (LOCK) {
            needPresence = postedKey == null || !key.equals(postedKey) || now - postedAt >= HEARTBEAT_MS;
        }
        if (needPresence) {
            try {
                ensureVisible(token);
                FroglogClient.setNowPlaying(token, target.id, FroglogNowPlaying.gameType(target.live),
                        target.title, FroglogNowPlaying.startedAt(playing.startTimeMs));
                synchronized (LOCK) {
                    postedKey = key;
                    postedAt = now;
                    lastPlaying = playing;
                    lastTarget = target;
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not set Froglog now playing", e);
            }
        } else {
            synchronized (LOCK) {
                lastPlaying = playing;
                lastTarget = target;
            }
        }
        logRunning(context, token, playing, target);
    }

    /**
     * Now-playing is presence only. A real play session is
     * {@code POST /games/:id/sessions}. Do not wait for Cocoon to finalize.
     */
    private static void logRunning(Context context, String token, CocoonLibrary.Playing playing, Target target) {
        if (context == null || token == null || playing == null || target == null) {
            return;
        }
        String sync = playing.title + ":" + playing.startTimeMs;
        if (FroglogStore.posted(context, sync)) {
            return;
        }
        int minutes = FroglogTracking.playMinutes(0, playing.startTimeMs, System.currentTimeMillis());
        if (minutes < 1) {
            return;
        }
        try {
            FroglogGame game = FroglogClient.ownedGame(token, playing.title, playing.platformId);
            if (game == null) {
                game = new FroglogGame(target.id, target.live, target.title, playing.platformId,
                        null, "", "", null, 0, "", 0);
            }
            FroglogClient.Logged logged = FroglogClient.logSession(token, game, day(playing.startTimeMs),
                    FroglogMatch.hoursFromMinutes(minutes), "cocoon:" + sync, FroglogSubmit.NOTES);
            FroglogStore.link(context, FroglogMatch.linkKey(playing.title, playing.platformId),
                    logged.id, logged.live);
            FroglogStore.markPosted(context, sync);
            Log.i(TAG, "Logged Froglog session " + playing.title + " · " + minutes + "m id=" + logged.id);
        } catch (Exception e) {
            Log.w(TAG, "Could not log Froglog session while playing", e);
        }
    }

    private static void ensureVisible(String token) {
        synchronized (LOCK) {
            if (visibilityOn) {
                return;
            }
        }
        try {
            FroglogClient.showCurrentSession(token, true);
            synchronized (LOCK) {
                visibilityOn = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not enable Froglog current-session visibility", e);
        }
    }

    private static void clear(Context context, boolean force) {
        String token = FroglogStore.signedIn(context) ? FroglogStore.token(context) : null;
        CocoonLibrary.Playing playing;
        Target target;
        synchronized (LOCK) {
            if (!force && postedKey == null) {
                return;
            }
            playing = lastPlaying;
            target = lastTarget;
            postedKey = null;
            postedAt = 0;
            lastPlaying = null;
            lastTarget = null;
        }
        logClosed(context, token, playing, target);
        if (token == null) {
            return;
        }
        try {
            FroglogClient.clearNowPlaying(token);
        } catch (Exception e) {
            Log.w(TAG, "Could not clear Froglog now playing", e);
        }
    }

    /**
     * Now-playing does not create a play session. If Cocoon never inserts a finished
     * row, this still posts hours from the presence window that just ended.
     */
    private static void logClosed(Context context, String token, CocoonLibrary.Playing playing, Target target) {
        if (context == null || token == null || playing == null || target == null) {
            return;
        }
        String sync = playing.title + ":" + playing.startTimeMs;
        if (FroglogStore.posted(context, sync)) {
            return;
        }
        int minutes = FroglogTracking.playMinutes(0, playing.startTimeMs, System.currentTimeMillis());
        if (minutes < 1) {
            return;
        }
        try {
            FroglogGame game = new FroglogGame(target.id, target.live, target.title, playing.platformId,
                    null, "", "", null, 0, "", 0);
            FroglogClient.Logged logged = FroglogClient.logSession(token, game, day(playing.startTimeMs),
                    FroglogMatch.hoursFromMinutes(minutes), "cocoon:" + sync, FroglogSubmit.NOTES);
            FroglogStore.link(context, FroglogMatch.linkKey(playing.title, playing.platformId),
                    logged.id, logged.live);
            FroglogStore.markPosted(context, sync);
            Log.i(TAG, "Logged Froglog session " + playing.title + " · " + minutes + "m");
        } catch (Exception e) {
            Log.w(TAG, "Could not log Froglog session when play ended", e);
            FroglogStore.enqueuePending(context, playing.title, playing.platformId, minutes,
                    day(playing.startTimeMs), sync);
        }
    }

    private static String day(long startTimeMs) {
        long when = startTimeMs > 0 ? startTimeMs : System.currentTimeMillis();
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date(when));
    }

    private static Target resolve(Context context, CocoonLibrary.Playing playing) {
        String key = FroglogMatch.linkKey(playing.title, playing.platformId);
        String link = FroglogStore.link(context, key);
        if ("no".equals(link)) {
            return null;
        }
        if (link != null && link.indexOf(':') > 0) {
            boolean live = link.startsWith("live:");
            try {
                long id = Long.parseLong(link.substring(link.indexOf(':') + 1));
                return new Target(id, live, playing.title);
            } catch (NumberFormatException ignored) {
                // fall through to a library match
            }
        }
        FroglogClient.Recent recent = library(context);
        if (recent == null || recent.games == null) {
            return null;
        }
        FroglogGame game = FroglogMatch.best(recent.games, playing.title, playing.platformId);
        if (game == null) {
            return null;
        }
        FroglogStore.link(context, key, game.id, game.live);
        return new Target(game.id, game.live, game.title);
    }

    private static FroglogClient.Recent library(Context context) {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (library != null && now - libraryAt < LIBRARY_MS) {
                return library;
            }
        }
        FroglogClient.Recent recent = FroglogClient.library(FroglogStore.token(context));
        synchronized (LOCK) {
            library = recent;
            libraryAt = now;
        }
        return recent;
    }

    private static final class Target {
        final long id;
        final boolean live;
        final String title;

        Target(long id, boolean live, String title) {
            this.id = id;
            this.live = live;
            this.title = title;
        }
    }
}
