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
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
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
    public static final String ACTION_FILTER = "rip.moth.cocoonshell.froglog.FILTER";
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
                    if (pending != null) {
                        pending.finish();
                    }
                }
            }
        }, "froglog-widget").start();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        onUpdate(context, manager, new int[] {appWidgetId});
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_FILTER.equals(intent.getAction())) {
            FroglogStore.setFilter(context, FroglogGames.nextFilter(FroglogStore.filter(context)));
        }
        if (intent != null && (ACTION_REFRESH.equals(intent.getAction()) || ACTION_FILTER.equals(intent.getAction()))) {
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
            recent = FroglogClient.recentGames(token, username, SLOTS, FroglogStore.filter(context));
        }
        for (int id : ids) {
            manager.updateAppWidget(id, views(context, manager, id, username, recent));
        }
    }

    private static RemoteViews views(Context context, AppWidgetManager manager, int widgetId, String username, FroglogClient.Recent recent) {
        Bundle options = manager.getAppWidgetOptions(widgetId);
        Fit fit = fit(options == null ? Bundle.EMPTY : options);
        RemoteViews views = new RemoteViews(context.getPackageName(), layout(context, "froglog_widget"));
        views.setTextViewText(id(context, "froglog_title"), "Froglog");
        FroglogTheme.Palette palette = FroglogTheme.resolve(context);
        FroglogWidgetTheme.accent(context, views, palette, "froglog_title", "froglog_filter");
        FroglogWidgetTheme.ink(context, views, palette, "froglog_message",
                "froglog_name0", "froglog_name1", "froglog_name2", "froglog_name3");
        FroglogWidgetTheme.muted(context, views, palette, "froglog_subtitle",
                "froglog_meta0", "froglog_meta1", "froglog_meta2", "froglog_meta3");
        String filter = FroglogStore.filter(context);
        views.setTextViewText(id(context, "froglog_filter"), FroglogGames.filterLabel(filter));
        boolean signedIn = FroglogStore.signedIn(context);
        views.setViewVisibility(id(context, "froglog_subtitle"), View.GONE);
        if (!signedIn) {
            showMessage(context, views, "Sign in to Froglog");
        } else if (recent != null && recent.error != null) {
            showMessage(context, views, recent.error);
        } else if (recent == null || recent.games.isEmpty()) {
            showMessage(context, views, "No play history yet");
        } else {
            views.setViewVisibility(id(context, "froglog_message"), View.GONE);
            views.setViewVisibility(id(context, "froglog_row"), View.VISIBLE);
            List<FroglogGame> games = recent.games;
            for (int i = 0; i < SLOTS; i++) {
                int slot = id(context, "froglog_slot" + i);
                if (i >= games.size() || i >= fit.slots) {
                    views.setViewVisibility(slot, View.GONE);
                    continue;
                }
                FroglogGame game = games.get(i);
                views.setViewVisibility(slot, View.VISIBLE);
                views.setTextViewText(id(context, "froglog_name" + i), game.title);
                int meta = id(context, "froglog_meta" + i);
                if (fit.compact) {
                    views.setViewVisibility(meta, View.GONE);
                } else {
                    views.setViewVisibility(meta, View.VISIBLE);
                    views.setTextViewText(meta, game.meta);
                }
                int art = id(context, "froglog_art" + i);
                sizeCover(views, art, fit.coverDp);
                Bitmap cover = loadCover(game.coverUrl);
                if (cover != null) {
                    views.setImageViewBitmap(art, cover);
                } else {
                    views.setImageViewResource(art,
                            context.getResources().getIdentifier("froglog_cover_placeholder", "drawable", context.getPackageName()));
                }
                Intent detail = FroglogGameDetail.intent(context, game, true);
                detail.setData(Uri.parse("froglog://game/" + widgetId + "/" + i));
                views.setOnClickPendingIntent(slot, PendingIntent.getActivity(context, widgetId * 10 + i, detail,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        }
        Intent open = new Intent(context, FroglogPodActivity.class);
        open.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        open.setData(Uri.parse("froglog://widget/" + widgetId));
        views.setOnClickPendingIntent(id(context, "froglog_title"), PendingIntent.getActivity(context, widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent cycle = new Intent(context, FroglogRecentWidget.class);
        cycle.setAction(ACTION_FILTER);
        views.setOnClickPendingIntent(id(context, "froglog_filter"), PendingIntent.getBroadcast(context, widgetId + 50, cycle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
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
                int max = 96;
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

    /** Cocoon turns provider dp into grid cells with ceil(dp / 74). Covers stay inside that cell. */
    private static Fit fit(Bundle options) {
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (width <= 0) {
            width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 222);
        }
        if (height <= 0) {
            height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 148);
        }
        boolean compact = height < 100;
        int text = compact ? 16 : 32;
        int innerW = Math.max(48, width - 16);
        int innerH = Math.max(36, height - 16 - 22 - text);
        int slots = SLOTS;
        int gap = 6;
        int cover = Math.min(innerH, (innerW - gap * (slots - 1)) / slots);
        while (slots > 3 && cover < 40) {
            slots--;
            cover = Math.min(innerH, (innerW - gap * (slots - 1)) / slots);
        }
        cover = Math.max(40, Math.min(cover, 72));
        return new Fit(cover, slots, compact);
    }

    private static void sizeCover(RemoteViews views, int viewId, int coverDp) {
        if (Build.VERSION.SDK_INT >= 31) {
            views.setViewLayoutWidth(viewId, coverDp, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(viewId, coverDp, TypedValue.COMPLEX_UNIT_DIP);
        }
    }

    private static final class Fit {
        final int coverDp;
        final int slots;
        final boolean compact;

        Fit(int coverDp, int slots, boolean compact) {
            this.coverDp = coverDp;
            this.slots = slots;
            this.compact = compact;
        }
    }

    private static int layout(Context context, String name) {
        return context.getResources().getIdentifier(name, "layout", context.getPackageName());
    }

    private static int id(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }
}
