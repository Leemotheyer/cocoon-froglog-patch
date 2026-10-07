package rip.moth.cocoonshell.froglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Recent Froglog games, laid out like Cocoon's Recently played: a header, then square cover
 * rows with the title and play time. Wide tiles use two columns, narrow tiles show covers
 * only. It does not read or replace the local Recently played widget.
 */
public class FroglogRecentWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "rip.moth.cocoonshell.froglog.REFRESH";
    public static final String ACTION_FILTER = "rip.moth.cocoonshell.froglog.FILTER";
    private static final int ROWS = 6;
    private static final int SLOTS = ROWS * 2;

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

    /** Redraws placed tiles, for example when Cocoon starts or stops a game. */
    static void refresh(Context context) {
        final Context app = context.getApplicationContext();
        synchronized (FroglogRecentWidget.class) {
            if (refresher == null) {
                refresher = new Handler(Looper.getMainLooper());
            }
            refresher.removeCallbacksAndMessages(null);
            // Session scans enqueue several rows at once; redraw once after they settle.
            refresher.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(app, FroglogRecentWidget.class);
                    intent.setAction(ACTION_REFRESH);
                    app.sendBroadcast(intent);
                }
            }, 1500);
        }
    }

    private static Handler refresher;

    static void render(Context context, AppWidgetManager manager, int[] ids) {
        String token = FroglogStore.token(context);
        String username = FroglogStore.username(context);
        FroglogClient.Recent recent = null;
        if (FroglogStore.signedIn(context)) {
            recent = FroglogClient.recentGames(token, username, SLOTS, FroglogStore.filter(context));
        }
        String playing = playingKey(context, recent);
        int waiting = FroglogStore.signedIn(context) ? FroglogStore.pending(context).size() : 0;
        for (int id : ids) {
            manager.updateAppWidget(id, views(context, manager, id, recent, playing, waiting));
        }
    }

    /** "game:12" or "live:4" for the Froglog game Cocoon is running now, without any network call. */
    private static String playingKey(Context context, FroglogClient.Recent recent) {
        CocoonLibrary.Playing playing = CocoonLibrary.playing(context);
        if (playing == null) {
            return null;
        }
        String link = FroglogStore.link(context, FroglogMatch.linkKey(playing.title, playing.platformId));
        if (link != null && link.indexOf(':') > 0) {
            return link;
        }
        if ("no".equals(link) || recent == null || recent.games == null) {
            return null;
        }
        FroglogGame match = FroglogMatch.best(recent.games, playing.title, playing.platformId);
        return match == null ? null : FroglogLinks.value(match.id, match.live);
    }

    /** How the tile is filled. Every size is in dp. */
    static final class Fit {
        boolean header;
        boolean coversOnly;
        boolean meta;
        boolean pending;
        int columns;
        int rows;
        int cover;
        float scale;
    }

    static Fit fit(FroglogWidgetArt.Box box, boolean hasPending) {
        Fit fit = new Fit();
        float e = box.scale;
        fit.scale = e;
        int gap = Math.round(FroglogWidgetArt.clamp(4 * e, 3, 10));
        int width = box.widthDp;
        int height = box.heightDp;
        if (width < 100) {
            fit.coversOnly = true;
            fit.columns = 1;
            fit.cover = Math.min(width, Math.round(FroglogWidgetArt.clamp(64 * e, 40, 84)));
            fit.rows = Math.max(1, Math.min(ROWS, (height + gap) / (fit.cover + gap)));
            if (fit.rows == 1) {
                fit.cover = Math.max(20, Math.min(width, height));
            }
            return fit;
        }
        fit.header = true;
        int headerH = Math.round(FroglogWidgetArt.clamp(16 * e, 14, 24)) + 6;
        int pendingH = 14;
        fit.pending = hasPending && height - headerH - pendingH >= 2 * 28 + gap;
        int available = height - headerH - (fit.pending ? pendingH : 0);
        fit.cover = Math.round(FroglogWidgetArt.clamp(28 * e, 22, 42));
        fit.rows = (available + gap) / (fit.cover + gap);
        if (fit.rows < 1) {
            fit.rows = 1;
            fit.cover = Math.max(18, available);
        }
        fit.rows = Math.min(ROWS, fit.rows);
        fit.meta = fit.cover >= 26;
        fit.columns = width >= 200 ? 2 : 1;
        return fit;
    }

    private static RemoteViews views(Context context, AppWidgetManager manager, int widgetId,
            FroglogClient.Recent recent, String playing, int waiting) {
        FroglogWidgetArt.Box box = FroglogWidgetArt.box(manager.getAppWidgetOptions(widgetId), 222, 148);
        Fit fit = fit(box, waiting > 0);
        float e = fit.scale;
        RemoteViews views = new RemoteViews(context.getPackageName(), layout(context, "froglog_widget"));
        int root = id(context, "froglog_root");
        views.setViewPadding(root, dp(context, box.padH), dp(context, box.padV), dp(context, box.padH),
                dp(context, box.padV));
        FroglogTheme.Palette palette = FroglogTheme.resolve(context);
        FroglogWidgetTheme.accent(context, views, palette, "froglog_title", "froglog_filter", "froglog_pending");
        FroglogWidgetTheme.ink(context, views, palette, "froglog_message");
        for (int i = 0; i < SLOTS; i++) {
            FroglogWidgetTheme.ink(context, views, palette, "froglog_name" + i);
            FroglogWidgetTheme.muted(context, views, palette, "froglog_meta" + i);
        }

        views.setViewVisibility(id(context, "froglog_header"), fit.header ? View.VISIBLE : View.GONE);
        views.setTextViewText(id(context, "froglog_title"), "Froglog");
        views.setTextViewTextSize(id(context, "froglog_title"), TypedValue.COMPLEX_UNIT_SP,
                FroglogWidgetArt.clamp(13 * e, 11, 18));
        String filter = FroglogStore.filter(context);
        views.setTextViewText(id(context, "froglog_filter"), FroglogGames.filterLabel(filter));
        views.setTextViewTextSize(id(context, "froglog_filter"), TypedValue.COMPLEX_UNIT_SP,
                FroglogWidgetArt.clamp(11 * e, 9, 15));
        views.setTextViewTextSize(id(context, "froglog_message"), TypedValue.COMPLEX_UNIT_SP,
                FroglogWidgetArt.clamp(11 * e, 9, 15));

        PendingIntent pod = podIntent(context, widgetId);
        views.setOnClickPendingIntent(root, pod);
        views.setOnClickPendingIntent(id(context, "froglog_title"), pod);
        views.setOnClickPendingIntent(id(context, "froglog_title_icon"), pod);
        Intent cycle = new Intent(context, FroglogRecentWidget.class);
        cycle.setAction(ACTION_FILTER);
        views.setOnClickPendingIntent(id(context, "froglog_filter"), PendingIntent.getBroadcast(context, widgetId + 50, cycle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        int pendingView = id(context, "froglog_pending");
        if (fit.pending) {
            views.setViewVisibility(pendingView, View.VISIBLE);
            views.setTextViewText(pendingView, waiting == 1 ? "1 session waiting to log" : waiting + " sessions waiting to log");
            views.setOnClickPendingIntent(pendingView, pod);
        } else {
            views.setViewVisibility(pendingView, View.GONE);
        }

        if (!FroglogStore.signedIn(context)) {
            showMessage(context, views, fit, "Sign in to Froglog");
            return views;
        }
        if (recent != null && recent.error != null) {
            showMessage(context, views, fit, recent.error);
            return views;
        }
        List<FroglogGame> games = recent == null ? null : recent.games;
        if (games == null || games.isEmpty()) {
            showMessage(context, views, fit, FroglogGames.FILTER_RECENT.equals(filter)
                    ? "No play history yet" : "No " + FroglogGames.filterLabel(filter).toLowerCase(java.util.Locale.ROOT) + " games");
            return views;
        }

        views.setViewVisibility(id(context, "froglog_message_box"), View.GONE);
        views.setViewVisibility(id(context, "froglog_list"), View.VISIBLE);
        views.setViewVisibility(id(context, "froglog_col1"), fit.columns > 1 ? View.VISIBLE : View.GONE);
        float nameSize = FroglogWidgetArt.clamp(12 * e, 10, 16);
        float metaSize = FroglogWidgetArt.clamp(10 * e, 9, 13);
        int placeholder = FroglogWidgetArt.withAlpha(palette.muted, 0x33);
        int shown = Math.min(games.size(), fit.rows * fit.columns);
        for (int i = 0; i < SLOTS; i++) {
            int col = i / ROWS;
            int row = i % ROWS;
            int index = row * fit.columns + col;
            int slot = id(context, "froglog_slot" + i);
            if (col >= fit.columns || row >= fit.rows || index >= shown) {
                views.setViewVisibility(slot, View.GONE);
                continue;
            }
            FroglogGame game = games.get(index);
            views.setViewVisibility(slot, View.VISIBLE);
            views.setInt(slot, "setGravity", fit.coversOnly ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
            int art = id(context, "froglog_art" + i);
            Bitmap source = FroglogImages.fetch(game.coverUrl, FroglogWidgetArt.px(context, fit.cover * 2));
            views.setImageViewBitmap(art, FroglogWidgetArt.cover(context, source, fit.cover, placeholder));
            views.setContentDescription(art, game.title);
            views.setViewVisibility(id(context, "froglog_text" + i), fit.coversOnly ? View.GONE : View.VISIBLE);
            int name = id(context, "froglog_name" + i);
            views.setTextViewText(name, game.title);
            views.setTextViewTextSize(name, TypedValue.COMPLEX_UNIT_SP, nameSize);
            int meta = id(context, "froglog_meta" + i);
            boolean now = playing != null && playing.equals(FroglogLinks.value(game.id, game.live));
            if (fit.meta || now) {
                views.setViewVisibility(meta, View.VISIBLE);
                views.setTextViewText(meta, now ? "Playing now" : FroglogGames.widgetMeta(game));
                views.setTextViewTextSize(meta, TypedValue.COMPLEX_UNIT_SP, metaSize);
                if (now) {
                    FroglogWidgetTheme.accent(context, views, palette, "froglog_meta" + i);
                }
            } else {
                views.setViewVisibility(meta, View.GONE);
            }
            Intent detail = FroglogGameDetail.intent(context, game, true);
            detail.setData(Uri.parse("froglog://game/" + widgetId + "/" + i));
            views.setOnClickPendingIntent(slot, PendingIntent.getActivity(context, widgetId * 100 + i, detail,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        return views;
    }

    private static void showMessage(Context context, RemoteViews views, Fit fit, String message) {
        views.setViewVisibility(id(context, "froglog_list"), View.GONE);
        views.setViewVisibility(id(context, "froglog_message_box"), View.VISIBLE);
        views.setViewVisibility(id(context, "froglog_message_icon"), fit.header ? View.VISIBLE : View.GONE);
        views.setTextViewText(id(context, "froglog_message"), message);
    }

    private static PendingIntent podIntent(Context context, int widgetId) {
        Intent open = new Intent(context, FroglogPodActivity.class);
        open.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        open.setData(Uri.parse("froglog://widget/" + widgetId));
        return PendingIntent.getActivity(context, widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int dp(Context context, int value) {
        return FroglogWidgetArt.px(context, value);
    }

    private static int layout(Context context, String name) {
        return context.getResources().getIdentifier(name, "layout", context.getPackageName());
    }

    private static int id(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }
}
