package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.util.Log;

/**
 * Reports the open Cocoon session to Froglog the same way LilyPad does:
 * {@code PUT /users/me/now-playing} while a game is running, then DELETE when it ends.
 * Play sessions are posted by {@link FroglogSync}, not here.
 */
public final class FroglogPresence {
    private static final String TAG = "FroglogWidget";
    private static final long POLL_MS = 15L * 1000L;
    /** While a game is open, notice it closing within about a second. */
    private static final long ACTIVE_MS = 1000L;
    private static final long HEARTBEAT_MS = 2L * 60L * 1000L;
    private static final long LIBRARY_MS = 5L * 60L * 1000L;

    private static final Object LOCK = new Object();
    /** After a game closes, keep scanning for a few seconds so a late session row is not left for the idle poll. */
    private static final long WATCH_MS = 4000L;
    private static boolean started;
    private static long watchUntil;
    private static String postedKey;
    private static long postedAt;
    private static boolean visibilityOn;
    private static FroglogClient.Recent library;
    private static long libraryAt;
    private static String unmatched;

    private FroglogPresence() {}

    public static void start(Context context) {
        synchronized (LOCK) {
            if (started || context == null) {
                return;
            }
            started = true;
        }
        final Context app = context.getApplicationContext();
        // A previous run may have died while in game. The first poll clears it if nothing is running.
        if (FroglogStore.signedIn(app)) {
            FroglogStore.setPresenceOn(app, true);
        }
        FroglogSync.start(app);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    long wait = POLL_MS;
                    try {
                        wait = sync(app);
                    } catch (Throwable t) {
                        Log.e(TAG, "Froglog presence poll failed", t);
                    }
                    try {
                        FroglogSync.tick(app);
                    } catch (Throwable t) {
                        Log.e(TAG, "Froglog session sync failed", t);
                    }
                    FroglogSocial.warm(app);
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "froglog-presence").start();
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

    /** How long to wait before the next poll. A running game is watched closely so logging starts as it ends. */
    static long sync(Context context) {
        if (!FroglogStore.signedIn(context)) {
            synchronized (LOCK) {
                postedKey = null;
                postedAt = 0;
            }
            return POLL_MS;
        }
        CocoonLibrary.Playing playing = CocoonLibrary.playing(context);
        if (playing == null) {
            boolean wasOn = FroglogStore.presenceOn(context);
            clear(context, false);
            long now = System.currentTimeMillis();
            if (wasOn) {
                watchUntil = now + WATCH_MS;
                FroglogSync.kick(context);
            }
            // The idle poll is 15s. Stay on the short interval until a just-finished session is visible.
            return now < watchUntil ? ACTIVE_MS : POLL_MS;
        }
        Target target = resolve(context, playing);
        if (target == null) {
            clear(context, false);
            return ACTIVE_MS;
        }
        String key = FroglogNowPlaying.key(target.id, target.live, playing.startTimeMs);
        long now = System.currentTimeMillis();
        boolean needPresence;
        synchronized (LOCK) {
            needPresence = postedKey == null || !key.equals(postedKey) || now - postedAt >= HEARTBEAT_MS;
        }
        if (!needPresence) {
            return ACTIVE_MS;
        }
        String token = FroglogStore.token(context);
        try {
            ensureVisible(token);
            FroglogStore.setPresenceOn(context, true);
            FroglogClient.setNowPlaying(token, target.id, FroglogNowPlaying.gameType(target.live),
                    target.title, FroglogNowPlaying.startedAt(playing.startTimeMs));
            boolean first;
            synchronized (LOCK) {
                first = !key.equals(postedKey);
                postedKey = key;
                postedAt = now;
            }
            if (first) {
                Log.i(TAG, "Set Froglog now playing " + target.title);
                FroglogRecentWidget.refresh(context);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not set Froglog now playing", e);
        }
        return ACTIVE_MS;
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

    /**
     * Sends DELETE whenever Froglog may still show this user in game. The flag is
     * persisted, so an offline or interrupted clear is retried on the next poll.
     */
    private static void clear(Context context, boolean force) {
        synchronized (LOCK) {
            postedKey = null;
            postedAt = 0;
        }
        if (!FroglogStore.signedIn(context)) {
            return;
        }
        if (!force && !FroglogStore.presenceOn(context)) {
            return;
        }
        try {
            FroglogClient.clearNowPlaying(FroglogStore.token(context));
            FroglogStore.setPresenceOn(context, false);
            Log.i(TAG, "Cleared Froglog now playing");
            FroglogRecentWidget.refresh(context);
        } catch (Exception e) {
            FroglogStore.setPresenceOn(context, true);
            Log.w(TAG, "Could not clear Froglog now playing, will retry", e);
        }
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
            synchronized (LOCK) {
                if (!key.equals(unmatched)) {
                    unmatched = key;
                    Log.i(TAG, "Cocoon is playing " + playing.title + " (" + playing.platformId
                            + ") but it is not in the Froglog library yet");
                }
            }
            return null;
        }
        FroglogStore.link(context, playing.title, playing.platformId, game.id, game.live);
        return new Target(game.id, game.live, game.title);
    }

    private static FroglogClient.Recent library(Context context) {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (library != null && library.error == null && now - libraryAt < LIBRARY_MS) {
                return library;
            }
        }
        FroglogClient.Recent recent = FroglogClient.library(FroglogStore.token(context));
        if (recent.error == null) {
            FroglogStore.rememberLibrary(context, recent.games);
        } else if (!"Sign in to Froglog again".equals(recent.error)) {
            java.util.List<FroglogGame> cached = FroglogStore.cachedGames(context);
            if (!cached.isEmpty()) {
                recent = new FroglogClient.Recent(cached, null, true);
            }
        }
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
