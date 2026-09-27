package rip.moth.cocoonshell.froglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Grid widget of recent Froglog games. Cocoon hosts it as its own tile.
 * It does not read or replace the local Recently played widget.
 */
public class FroglogRecentWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "rip.moth.cocoonshell.froglog.REFRESH";
    private static final int SLOTS = 4;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        final int[] ids = appWidgetIds.clone();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    render(app, AppWidgetManager.getInstance(app), ids);
                } finally {
                    pending.finish();
                }
            }
        }, "froglog-widget").start();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, FroglogRecentWidget.class));
            onUpdate(context, manager, ids);
        }
    }

    static void render(Context context, AppWidgetManager manager, int[] ids) {
        String token = FroglogStore.token(context);
        String username = FroglogStore.username(context);
        FroglogClient.Recent recent = null;
        if (FroglogStore.signedIn(context)) {
            recent = FroglogClient.recentGames(token, username, SLOTS);
        }
        for (int id : ids) {
            manager.updateAppWidget(id, views(context, id, username, recent));
        }
    }

    private static RemoteViews views(Context context, int widgetId, String username, FroglogClient.Recent recent) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout(context, "froglog_widget"));
        views.setTextViewText(id(context, "froglog_title"), "Froglog");
        boolean signedIn = FroglogStore.signedIn(context);
        if (!signedIn) {
            showMessage(context, views, "Sign in to see your recent Froglog games");
            views.setTextViewText(id(context, "froglog_subtitle"), "Recent games");
        } else if (recent != null && recent.error != null) {
            showMessage(context, views, recent.error);
            views.setTextViewText(id(context, "froglog_subtitle"), username);
        } else if (recent == null || recent.games.isEmpty()) {
            showMessage(context, views, "No public Froglog games yet");
            views.setTextViewText(id(context, "froglog_subtitle"), username);
        } else {
            views.setViewVisibility(id(context, "froglog_message"), View.GONE);
            views.setViewVisibility(id(context, "froglog_row"), View.VISIBLE);
            views.setTextViewText(id(context, "froglog_subtitle"), username);
            List<FroglogGame> games = recent.games;
            for (int i = 0; i < SLOTS; i++) {
                int slot = id(context, "froglog_slot" + i);
                if (i >= games.size()) {
                    views.setViewVisibility(slot, View.GONE);
                    continue;
                }
                FroglogGame game = games.get(i);
                views.setViewVisibility(slot, View.VISIBLE);
                views.setTextViewText(id(context, "froglog_name" + i), game.title);
                views.setTextViewText(id(context, "froglog_meta" + i), game.meta);
                Bitmap cover = loadCover(game.coverUrl);
                if (cover != null) {
                    views.setImageViewBitmap(id(context, "froglog_art" + i), cover);
                } else {
                    views.setImageViewResource(id(context, "froglog_art" + i),
                            context.getResources().getIdentifier("froglog_cover_placeholder", "drawable", context.getPackageName()));
                }
            }
        }
        Intent open = new Intent(context, FroglogWidgetConfig.class);
        open.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        open.setData(Uri.parse("froglog://widget/" + widgetId));
        PendingIntent pending = PendingIntent.getActivity(context, widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(id(context, "froglog_root"), pending);
        return views;
    }

    private static void showMessage(Context context, RemoteViews views, String message) {
        views.setViewVisibility(id(context, "froglog_row"), View.GONE);
        views.setViewVisibility(id(context, "froglog_message"), View.VISIBLE);
        views.setTextViewText(id(context, "froglog_message"), message);
    }

    private static Bitmap loadCover(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            if (conn.getResponseCode() >= 400) {
                return null;
            }
            InputStream in = conn.getInputStream();
            try {
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw == null) {
                    return null;
                }
                int max = 160;
                int width = raw.getWidth();
                int height = raw.getHeight();
                if (width <= max && height <= max) {
                    return raw;
                }
                float scale = Math.min(max / (float) width, max / (float) height);
                return Bitmap.createScaledBitmap(raw, Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), true);
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static int layout(Context context, String name) {
        return context.getResources().getIdentifier(name, "layout", context.getPackageName());
    }

    private static int id(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }
}
