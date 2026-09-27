package rip.moth.cocoonshell.froglog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import rip.moth.cocoonshell.data.model.GameSession;

/**
 * Called when Cocoon inserts one finished play session.
 * The first time a game is seen, Froglog asks before writing anything.
 */
public final class FroglogSessionBridge {
    private static final String TAG = "FroglogWidget";
    private static final long RECENT_MS = 3L * 60L * 1000L;

    private FroglogSessionBridge() {}

    public static void onInserted(GameSession session) {
        try {
            Context context = CatalogHook.context;
            if (context == null || session == null || !FroglogStore.signedIn(context)) {
                return;
            }
            int minutes = session.getDurationMinutes();
            long end = session.getEndTime();
            long now = System.currentTimeMillis();
            if (minutes < 1 || end <= 0 || now - end > RECENT_MS || end - now > 60L * 1000L) {
                return;
            }
            String title = session.getGameName();
            if (title == null || title.trim().isEmpty()) {
                return;
            }
            String platform = session.getPlatformId() == null ? "" : session.getPlatformId();
            String sync = session.getClientSessionId();
            if (sync == null || sync.isEmpty()) {
                sync = title + ":" + session.getStartTime();
            }
            if (FroglogStore.posted(context, sync)) {
                return;
            }
            String key = FroglogMatch.linkKey(title, platform);
            String link = FroglogStore.link(context, key);
            if ("no".equals(link)) {
                return;
            }
            String date = date(session);
            if (link != null && link.indexOf(':') > 0) {
                postLinked(context, link, title, platform, minutes, date, sync);
                return;
            }
            boolean fresh = !FroglogStore.hasPending(context, sync);
            FroglogStore.enqueuePending(context, title, platform, minutes, date, sync);
            if (fresh) {
                ask(context, title, platform, minutes, date, sync);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Session hook failed", t);
        }
    }

    private static void postLinked(final Context context, String link, final String title, final String platform,
            final int minutes, final String date, final String sync) {
        final boolean live = link.startsWith("live:");
        final long id;
        try {
            id = Long.parseLong(link.substring(link.indexOf(':') + 1));
        } catch (NumberFormatException e) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FroglogGame game = new FroglogGame(id, live, title, platform, null, "", "", null, 0, "", 0);
                    FroglogClient.Logged logged = FroglogClient.logSession(FroglogStore.token(context), game, date,
                            FroglogMatch.hoursFromMinutes(minutes), "cocoon:" + sync, FroglogSubmit.NOTES);
                    if (logged.live != live || logged.id != id) {
                        FroglogStore.link(context, FroglogMatch.linkKey(title, platform), logged.id, logged.live);
                    }
                    FroglogStore.markPosted(context, sync);
                    FroglogStore.removePending(context, sync);
                } catch (Exception e) {
                    Log.e(TAG, "Could not log linked session", e);
                    FroglogStore.enqueuePending(context, title, platform, minutes, date, sync);
                    FroglogStore.pendingError(context, sync, e.getMessage() == null ? "Could not log the session" : e.getMessage());
                    ask(context, title, platform, minutes, date, sync);
                }
            }
        }, "froglog-session").start();
    }

    private static void ask(Context context, String title, String platform, int minutes, String date, String sync) {
        Intent open = new Intent(context, FroglogMapActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra(FroglogMapActivity.EXTRA_TITLE, title);
        open.putExtra(FroglogMapActivity.EXTRA_PLATFORM, platform);
        open.putExtra(FroglogMapActivity.EXTRA_MINUTES, minutes);
        open.putExtra(FroglogMapActivity.EXTRA_DATE, date);
        open.putExtra(FroglogMapActivity.EXTRA_SYNC, sync);
        open.setData(android.net.Uri.parse("froglog://session/" + sync));
        PendingIntent pending = PendingIntent.getActivity(context, sync.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder = channel(context, manager);
        builder.setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("Map this session to Froglog")
                .setContentText(title + " · " + minutes + "m")
                .setAutoCancel(true)
                .setContentIntent(pending);
        manager.notify(Math.abs(sync.hashCode()), builder.build());
        try {
            context.startActivity(open);
        } catch (RuntimeException ignored) {
            // Android blocks background activity starts. The notification remains the confirmation.
        }
    }

    private static Notification.Builder channel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("froglog", "Froglog", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
            return new Notification.Builder(context, "froglog");
        }
        return new Notification.Builder(context);
    }

    private static String date(GameSession session) {
        String date = session.getDate();
        if (date != null && date.length() >= 10 && date.charAt(4) == '-') {
            return date.substring(0, 10);
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
    }
}
