package rip.moth.cocoonshell.froglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Month hours from GET /api/stats. Larger tiles add week, year, finished, streak, and the
 * most played game this month. Taps open the pod on the matching library filter.
 */
public class FroglogStatsWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        final int[] ids = appWidgetIds.clone();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AppWidgetManager widgets = AppWidgetManager.getInstance(app);
                    boolean wantTop = false;
                    for (int id : ids) {
                        wantTop |= size(widgets, id).showTop;
                    }
                    FroglogClient.Stats stats = null;
                    if (FroglogStore.signedIn(app)) {
                        stats = FroglogClient.stats(FroglogStore.token(app), wantTop);
                    }
                    Bitmap cover = null;
                    if (wantTop && stats != null && stats.summary != null) {
                        cover = FroglogImages.fetch(stats.summary.topCover, 96);
                    }
                    for (int id : ids) {
                        widgets.updateAppWidget(id, views(app, id, size(widgets, id), stats, cover));
                    }
                } finally {
                    if (pending != null) {
                        pending.finish();
                    }
                }
            }
        }, "froglog-stats").start();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        onUpdate(context, manager, new int[] {appWidgetId});
    }

    static final class Size {
        boolean showFigures;
        boolean showTop;
        boolean showStreak;
    }

    private static Size size(AppWidgetManager manager, int id) {
        Bundle options = manager.getAppWidgetOptions(id);
        int width = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
        int height = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (width <= 0 && options != null) {
            width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 70);
        }
        if (height <= 0 && options != null) {
            height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 70);
        }
        Size size = new Size();
        size.showStreak = width >= 120;
        size.showFigures = width >= 140;
        size.showTop = width >= 140 && height >= 140;
        return size;
    }

    private static RemoteViews views(Context context, int widgetId, Size size, FroglogClient.Stats stats, Bitmap cover) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                context.getResources().getIdentifier("froglog_stats", "layout", context.getPackageName()));
        FroglogTheme.Palette palette = FroglogTheme.resolve(context);
        FroglogWidgetTheme.accent(context, views, palette, "froglog_stats_title", "froglog_stats_streak",
                "froglog_stats_top_hours");
        FroglogWidgetTheme.ink(context, views, palette, "froglog_stats_month", "froglog_stats_year",
                "froglog_stats_message", "froglog_stats_week", "froglog_stats_year_hours", "froglog_stats_done",
                "froglog_stats_top_name");
        FroglogWidgetTheme.muted(context, views, palette, "froglog_stats_rate", "froglog_stats_week_label",
                "froglog_stats_year_label", "froglog_stats_done_label", "froglog_stats_top_label");

        int month = res(context, "froglog_stats_month");
        int year = res(context, "froglog_stats_year");
        int message = res(context, "froglog_stats_message");
        int figures = res(context, "froglog_stats_figures");
        int top = res(context, "froglog_stats_top");
        int streak = res(context, "froglog_stats_streak");
        views.setTextViewText(res(context, "froglog_stats_title"), "Froglog");
        views.setViewVisibility(res(context, "froglog_stats_rate"), View.GONE);
        views.setViewVisibility(figures, View.GONE);
        views.setViewVisibility(top, View.GONE);
        views.setViewVisibility(streak, View.GONE);
        FroglogStatsSummary summary = stats == null ? null : stats.summary;
        if (!FroglogStore.signedIn(context)) {
            show(views, month, year, message, "Sign in");
        } else if (stats != null && stats.error != null) {
            show(views, month, year, message, stats.error);
        } else if (stats == null) {
            show(views, month, year, message, "No stats");
        } else {
            views.setViewVisibility(message, View.GONE);
            views.setViewVisibility(month, View.VISIBLE);
            views.setViewVisibility(year, View.VISIBLE);
            views.setTextViewText(month, stats.compactHours);
            views.setTextViewText(year, "this month");
            if (summary != null) {
                if (size.showStreak && summary.streak > 1) {
                    views.setViewVisibility(streak, View.VISIBLE);
                    views.setTextViewText(streak, summary.streak + "-day streak");
                }
                if (size.showFigures) {
                    views.setViewVisibility(figures, View.VISIBLE);
                    views.setTextViewText(res(context, "froglog_stats_week"), hours(summary.weekHours));
                    views.setTextViewText(res(context, "froglog_stats_year_hours"), hours(summary.yearHours));
                    views.setTextViewText(res(context, "froglog_stats_done"), String.valueOf(summary.yearCompleted));
                    views.setTextViewText(res(context, "froglog_stats_done_label"), "done this year");
                }
                if (size.showTop && summary.topTitle != null) {
                    views.setViewVisibility(top, View.VISIBLE);
                    views.setTextViewText(res(context, "froglog_stats_top_name"), summary.topTitle);
                    views.setTextViewText(res(context, "froglog_stats_top_hours"), hours(summary.topHours));
                    int art = res(context, "froglog_stats_top_art");
                    if (cover != null) {
                        views.setImageViewBitmap(art, cover);
                    } else {
                        views.setImageViewResource(art, context.getResources().getIdentifier(
                                "froglog_cover_placeholder", "drawable", context.getPackageName()));
                    }
                    if (summary.topId > 0) {
                        FroglogGame game = new FroglogGame(summary.topId, summary.topLive, summary.topTitle, null,
                                summary.topCover, null, null, null, 0, hours(summary.topHours) + " this month", 0L);
                        Intent detail = FroglogGameDetail.intent(context, game, true);
                        detail.setData(Uri.parse("froglog://stats-top/" + widgetId));
                        views.setOnClickPendingIntent(top, PendingIntent.getActivity(context, widgetId * 10 + 5,
                                detail, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                    }
                }
            }
        }
        views.setOnClickPendingIntent(res(context, "froglog_stats_root"),
                pod(context, widgetId, 0, FroglogGames.FILTER_RECENT));
        views.setOnClickPendingIntent(res(context, "froglog_stats_week_box"),
                pod(context, widgetId, 1, FroglogGames.FILTER_RECENT));
        views.setOnClickPendingIntent(res(context, "froglog_stats_year_box"),
                pod(context, widgetId, 2, FroglogGames.FILTER_PROGRESS));
        views.setOnClickPendingIntent(res(context, "froglog_stats_done_box"),
                pod(context, widgetId, 3, FroglogGames.FILTER_COMPLETED));
        return views;
    }

    private static PendingIntent pod(Context context, int widgetId, int slot, String filter) {
        Intent open = new Intent(context, FroglogPodActivity.class);
        open.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        open.putExtra(FroglogPodActivity.EXTRA_FILTER, filter);
        open.setData(Uri.parse("froglog://stats/" + widgetId + "/" + slot));
        return PendingIntent.getActivity(context, widgetId * 10 + slot, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String hours(double value) {
        String label = FroglogGames.hoursLabel(Double.valueOf(value));
        return label == null ? "0h" : label;
    }

    private static void show(RemoteViews views, int month, int year, int message, String text) {
        views.setViewVisibility(month, View.GONE);
        views.setViewVisibility(year, View.GONE);
        views.setViewVisibility(message, View.VISIBLE);
        views.setTextViewText(message, text);
    }

    private static int res(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }
}
