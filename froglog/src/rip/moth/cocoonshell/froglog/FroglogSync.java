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
import java.util.List;

/**
 * Copies every finished Cocoon session (the rows the Log pod lists) into the Froglog queue,
 * then uploads the queue. A session that cannot reach Froglog stays queued and is sent
 * when the network comes back.
 */
public final class FroglogSync {
    private static final String TAG = "FroglogWidget";
    private static final long BACKFILL_MS = 48L * 60L * 60L * 1000L;
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

    static void scan(Context context) {
        long since = FroglogStore.sessionsSince(context);
        if (since <= 0) {
            since = System.currentTimeMillis() - BACKFILL_MS;
            FroglogStore.setSessionsSince(context, since);
        }
        List<CocoonLibrary.Session> rows = CocoonLibrary.sessionsSince(context, since);
        if (rows == null) {
            Log.w(TAG, "Could not read Cocoon play sessions");
            return;
        }
        if (rows.isEmpty()) {
            return;
        }
        Log.i(TAG, "Cocoon sessions to check: " + rows.size());
        long newest = since;
        for (int i = 0; i < rows.size(); i++) {
            CocoonLibrary.Session row = rows.get(i);
            newest = Math.max(newest, row.endTime);
            if (row.title.isEmpty()) {
                continue;
            }
            int minutes = FroglogTracking.playMinutes(row.durationMinutes, row.startTime, row.endTime);
            if (minutes < 1) {
                Log.i(TAG, "Skipped Cocoon session under a minute: " + row.title);
                continue;
            }
            String sync = syncRef(row);
            boolean grew = FroglogStore.posted(context, sync) && FroglogStore.remote(context, sync) != null
                    && minutes > FroglogStore.postedMinutes(context, sync);
            if (!grew && (FroglogStore.posted(context, sync) || FroglogStore.posted(context, row.title + ":" + row.startTime)
                    || FroglogStore.hasPending(context, sync))) {
                Log.i(TAG, "Cocoon session already handled: " + row.title + " · " + minutes + "m");
                continue;
            }
            if ("no".equals(FroglogStore.link(context, FroglogMatch.linkKey(row.title, row.platformId)))) {
                continue;
            }
            FroglogStore.enqueuePending(context, row.title, row.platformId, minutes, date(row), sync);
            Log.i(TAG, "Queued Cocoon session " + row.title + " · " + minutes + "m");
        }
        FroglogStore.setSessionsSince(context, newest);
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
                .setContentTitle("Map this session to Froglog")
                .setContentText(item.title + " · " + item.minutes + "m")
                .setAutoCancel(true)
                .setContentIntent(pending);
        manager.notify(Math.abs(item.sync.hashCode()), builder.build());
    }
}
