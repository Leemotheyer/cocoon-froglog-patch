package rip.moth.cocoonshell.froglog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies every finished Cocoon session (the rows the Log pod lists) into the Froglog queue,
 * then uploads the queue. A session that cannot reach Froglog stays queued and is sent
 * when the network comes back.
 */
public final class FroglogSync {
    private static final String TAG = "FroglogWidget";
    private static final long BACKFILL_MS = 48L * 60L * 60L * 1000L;
    private static final long WINDOW_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final long RETRY_MS = 60L * 1000L;
    public static final String OFFLINE = "Waiting for a connection";

    private static final Object LOCK = new Object();
    private static boolean running;
    private static boolean again;
    private static boolean networkHooked;
    private static volatile long retryAfter;

    private FroglogSync() {}

    public static void start(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        hookNetwork(app);
        kick(app);
    }

    /** Scan and upload now, ignoring the offline backoff. */
    public static void kick(Context context) {
        if (context == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                FroglogSync.run(app, true);
            }
        }, "froglog-sync").start();
    }

    /** Called from the presence poll. Uploads wait out the backoff after a network failure. */
    static void tick(Context context) {
        run(context, false);
    }

    private static void run(Context context, boolean force) {
        synchronized (LOCK) {
            if (running) {
                again = again || force;
                return;
            }
            running = true;
        }
        try {
            boolean repeat = true;
            while (repeat) {
                try {
                    if (FroglogStore.signedIn(context)) {
                        scan(context);
                        if (force || System.currentTimeMillis() >= retryAfter) {
                            flush(context);
                            repair(context);
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Froglog session sync failed", t);
                }
                synchronized (LOCK) {
                    repeat = again;
                    again = false;
                    force = true;
                }
            }
        } finally {
            synchronized (LOCK) {
                running = false;
            }
        }
    }

    /**
     * Rereads every recent row instead of moving a watermark. A session left paused finalizes
     * with its pause time as endTime, which can be earlier than a session that closed after it.
     * {@code sessionsSince} only stops the first run from sending older history.
     */
    static void scan(Context context) {
        long floor = FroglogStore.sessionsSince(context);
        if (floor <= 0) {
            floor = System.currentTimeMillis() - BACKFILL_MS;
            FroglogStore.setSessionsSince(context, floor);
        }
        long since = Math.max(floor, System.currentTimeMillis() - WINDOW_MS);
        List<CocoonLibrary.Session> rows = CocoonLibrary.sessionsSince(context, since);
        if (rows == null) {
            Log.w(TAG, "Could not read Cocoon play sessions");
            return;
        }
        HashSet<Long> games = new HashSet<Long>();
        for (int i = 0; i < rows.size(); i++) {
            games.add(Long.valueOf(rows.get(i).gameId));
        }
        Set<Long> open = games.isEmpty() ? games : CocoonLibrary.openGames(context, games);
        long now = System.currentTimeMillis();
        HashSet<Long> seen = new HashSet<Long>();
        for (int i = 0; i < rows.size(); i++) {
            CocoonLibrary.Session row = rows.get(i);
            boolean newest = seen.add(Long.valueOf(row.gameId));
            if (row.title.isEmpty()) {
                continue;
            }
            if (FroglogTracking.holdSession(row.endTime, now, newest, open.contains(Long.valueOf(row.gameId)))) {
                continue;
            }
            int minutes = FroglogTracking.playMinutes(row.durationMinutes, row.startTime, row.endTime);
            if (minutes < 1) {
                continue;
            }
            String sync = syncRef(row);
            boolean posted = FroglogStore.posted(context, sync);
            boolean grew = posted && FroglogStore.remote(context, sync) != null
                    && minutes > FroglogStore.postedMinutes(context, sync);
            if (!grew && (posted || FroglogStore.posted(context, row.title + ":" + row.startTime)
                    || FroglogStore.hasPending(context, sync))) {
                continue;
            }
            if ("no".equals(FroglogStore.link(context, FroglogMatch.linkKey(row.title, row.platformId)))) {
                continue;
            }
            FroglogStore.enqueuePending(context, row.title, row.platformId, minutes, date(row), sync);
            Log.i(TAG, "Queued Cocoon session " + row.title + " (" + row.platformId + ") · " + minutes + "m");
        }
    }

    static void flush(Context context) {
        List<FroglogQueue.Item> items = FroglogStore.pending(context);
        if (items.isEmpty()) {
            return;
        }
        String token = FroglogStore.token(context);
        FroglogClient.Recent library = null;
        for (int i = 0; i < items.size(); i++) {
            FroglogQueue.Item item = items.get(i);
            if (item.minutes < 1 || item.sync.isEmpty()) {
                FroglogStore.removePending(context, item.sync);
                continue;
            }
            if (FroglogStore.posted(context, item.sync)) {
                // Cocoon resumed a session that was already sent, so the Froglog row gets the new total.
                String remote = FroglogStore.remote(context, item.sync);
                if (remote == null || item.minutes <= FroglogStore.postedMinutes(context, item.sync)) {
                    FroglogStore.removePending(context, item.sync);
                    continue;
                }
                try {
                    FroglogClient.updateSessionHours(token, remote, FroglogMatch.hoursFromMinutes(item.minutes));
                    FroglogStore.markPosted(context, item.sync, item.minutes, remote);
                    FroglogStore.removePending(context, item.sync);
                    retryAfter = 0;
                    Log.i(TAG, "Updated Froglog session " + item.title + " to " + item.minutes + "m");
                    submitted(context, item, "Froglog session updated");
                } catch (IOException e) {
                    offline(context, item, OFFLINE);
                    return;
                } catch (Exception e) {
                    String message = e.getMessage() == null ? "Could not update the session" : e.getMessage();
                    FroglogStore.pendingError(context, item.sync, message);
                    Log.w(TAG, "Could not update Froglog session " + item.title + ": " + message);
                }
                continue;
            }
            String key = FroglogMatch.linkKey(item.title, item.platform);
            FroglogGame game = linked(FroglogStore.link(context, key), item);
            if (game == null) {
                if (library == null) {
                    library = FroglogClient.library(token);
                    if (library.error != null) {
                        offline(context, item, "Could not reach Froglog".equals(library.error) ? OFFLINE : library.error);
                        return;
                    }
                }
                game = FroglogMatch.best(library.games, item.title, item.platform);
                if (game == null) {
                    ask(context, item);
                    continue;
                }
                FroglogStore.link(context, key, game.id, game.live);
            }
            try {
                FroglogClient.Logged logged = FroglogClient.logSession(token, game, item.date,
                        FroglogMatch.hoursFromMinutes(item.minutes), "cocoon:" + item.sync, FroglogSubmit.NOTES);
                if (logged.live != game.live || logged.id != game.id) {
                    FroglogStore.link(context, key, logged.id, logged.live);
                }
                FroglogStore.markPosted(context, item.sync, item.minutes, logged.remote());
                FroglogStore.removePending(context, item.sync);
                retryAfter = 0;
                Log.i(TAG, "Logged Froglog session " + item.title + " · " + item.minutes + "m on " + item.date);
                submitted(context, item, "Session auto-submitted to Froglog");
            } catch (IOException e) {
                offline(context, item, OFFLINE);
                return;
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not log the session" : e.getMessage();
                FroglogStore.pendingError(context, item.sync, message);
                Log.w(TAG, "Could not log Froglog session " + item.title + ": " + message);
            }
        }
    }

    /** Once per linked game. Earlier builds could not read a game, so its tracking was never set up. */
    static void repair(Context context) {
        String token = FroglogStore.token(context);
        for (String game : FroglogStore.linkedGames(context)) {
            if (FroglogStore.repaired(context, game)) {
                continue;
            }
            try {
                long id = Long.parseLong(game.substring(game.indexOf(':') + 1));
                FroglogClient.repairGame(token, id, game.startsWith("live:"));
                FroglogStore.markRepaired(context, game);
            } catch (IOException e) {
                return;
            } catch (Exception e) {
                Log.w(TAG, "Could not repair Froglog game " + game + ": " + e.getMessage());
                FroglogStore.markRepaired(context, game);
            }
        }
    }

    private static void offline(Context context, FroglogQueue.Item item, String message) {
        retryAfter = System.currentTimeMillis() + RETRY_MS;
        FroglogStore.pendingError(context, item.sync, message);
        Log.i(TAG, "Froglog unreachable, keeping queued sessions: " + message);
    }

    private static FroglogGame linked(String link, FroglogQueue.Item item) {
        if (link == null || link.indexOf(':') <= 0 || "no".equals(link)) {
            return null;
        }
        try {
            long id = Long.parseLong(link.substring(link.indexOf(':') + 1));
            return new FroglogGame(id, link.startsWith("live:"), item.title, item.platform,
                    null, "", "", null, 0, "", 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String syncRef(CocoonLibrary.Session row) {
        return row.clientSessionId.isEmpty() ? row.title + ":" + row.startTime : row.clientSessionId;
    }

    private static String date(CocoonLibrary.Session row) {
        if (row.date.length() >= 10 && row.date.charAt(4) == '-') {
            return row.date.substring(0, 10);
        }
        long when = row.startTime > 0 ? row.startTime : System.currentTimeMillis();
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date(when));
    }

    private static void hookNetwork(final Context context) {
        synchronized (LOCK) {
            if (networkHooked || Build.VERSION.SDK_INT < 24) {
                return;
            }
            networkHooked = true;
        }
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (!FroglogStore.pending(context).isEmpty()) {
                        kick(context);
                    }
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not watch the network for queued Froglog sessions", e);
        }
    }

    /** One notification per unmatched session. The pod's New games list is the rest of the flow. */
    private static void ask(Context context, FroglogQueue.Item item) {
        if (FroglogStore.asked(context, item.sync)) {
            return;
        }
        FroglogStore.markAsked(context, item.sync);
        Intent open = new Intent(context, FroglogMapActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra(FroglogMapActivity.EXTRA_TITLE, item.title);
        open.putExtra(FroglogMapActivity.EXTRA_PLATFORM, item.platform);
        open.putExtra(FroglogMapActivity.EXTRA_MINUTES, item.minutes);
        open.putExtra(FroglogMapActivity.EXTRA_DATE, item.date);
        open.putExtra(FroglogMapActivity.EXTRA_SYNC, item.sync);
        open.setData(android.net.Uri.parse("froglog://session/" + item.sync));
        PendingIntent pending = PendingIntent.getActivity(context, item.sync.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notify(context, item.sync, "Map this session to Froglog", item.title + " · " + duration(item.minutes), pending);
    }

    /** LilyPad confirms a regular auto-submit the same way. Tapping opens the Froglog pod. */
    private static void submitted(Context context, FroglogQueue.Item item, String verb) {
        try {
            Intent open = new Intent(context, FroglogPodActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, 0x46524f47, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notify(context, item.sync, verb, item.title + " · " + duration(item.minutes), pending);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not show the Froglog session notice", e);
        }
    }

    private static void notify(Context context, String sync, String title, String text, PendingIntent pending) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                    new NotificationChannel("froglog", "Froglog", NotificationManager.IMPORTANCE_HIGH));
            builder = new Notification.Builder(context, "froglog");
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pending);
        manager.notify(Math.abs(sync.hashCode()), builder.build());
    }

    static String duration(int minutes) {
        if (minutes < 60) {
            return minutes + "m";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
