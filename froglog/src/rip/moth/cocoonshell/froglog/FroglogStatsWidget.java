package rip.moth.cocoonshell.froglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Froglog play time from GET /api/stats, laid out like Cocoon's Playtime widget. A 1x1 tile
 * shows the month total, wider tiles add daily bars, week / year / finished figures as the
 * width allows, the streak, and the most played game this month on tall tiles. Taps open
 * the pod on the matching library filter.
 */
public class FroglogStatsWidget extends AppWidgetProvider {
    private static final String[] FIGURES = {"week", "year", "done"};

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
                        wantTop |= fit(box(widgets, id), 7).top;
                    }
                    FroglogClient.Stats stats = null;
                    if (FroglogStore.signedIn(app)) {
                        stats = FroglogClient.stats(FroglogStore.token(app), wantTop);
                    }
                    for (int id : ids) {
                        FroglogWidgetArt.Box box = box(widgets, id);
                        widgets.updateAppWidget(id, views(app, id, box, fit(box, monthText(stats).length()), stats));
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

    private static FroglogWidgetArt.Box box(AppWidgetManager manager, int id) {
        return FroglogWidgetArt.box(manager.getAppWidgetOptions(id), 77, 77);
    }

    /** What the tile shows. Sizes are dp inside the tile padding. */
    static final class Fit {
        boolean header;
        boolean title;
        boolean centered;
        boolean sideChart;
        boolean chart;
        boolean top;
        int days;
        int figures;
        int chartW;
        int chartH;
        int cover;
        float big;
        float label;
        float scale;
    }

    /** {@code heroChars} is the length of the month total, which sets how much width it takes. */
    static Fit fit(FroglogWidgetArt.Box box, int heroChars) {
        Fit fit = new Fit();
        float e = box.scale;
        fit.scale = e;
        int w = box.widthDp;
        int h = box.heightDp;
        boolean narrow = w < 100;
        boolean shortTile = h < 90;
        fit.label = FroglogWidgetArt.clamp(10 * e, 9, 13);
        fit.big = shortTile ? FroglogWidgetArt.clamp(20 * e, 16, 26) : FroglogWidgetArt.clamp(24 * e, 18, 32);
        int heroW = Math.round(Math.max(Math.max(2, heroChars) * 0.58f * fit.big, 10 * 0.55f * fit.label));
        int heroH = Math.round(fit.big * 1.2f + 2 + fit.label * 1.2f);
        int figureW = Math.round(FroglogWidgetArt.clamp(46 * e, 40, 60)) + 12;
        fit.days = 7;
        if (narrow && shortTile) {
            fit.centered = true;
            return fit;
        }
        if (shortTile) {
            fit.sideChart = true;
            fit.chartH = h;
            int room = w - heroW - 10;
            fit.figures = Math.max(0, Math.min(3, (room - 110 + 12) / figureW));
            fit.chartW = Math.max(40, room - (fit.figures > 0 ? fit.figures * figureW - 2 : 0));
            fit.days = fit.chartW >= 84 ? 7 : fit.chartW >= 56 ? 5 : 3;
            return fit;
        }
        fit.header = true;
        fit.title = !narrow;
        int headerH = Math.round(FroglogWidgetArt.clamp(14 * e, 12, 20)) + 4;
        if (narrow) {
            fit.days = 3;
        } else {
            fit.figures = Math.max(0, Math.min(3, (w - heroW - 10 + 12) / figureW));
        }
        int left = h - headerH - heroH - 6;
        fit.cover = Math.round(FroglogWidgetArt.clamp(30 * e, 24, 40));
        int topH = fit.cover + 8;
        fit.top = !narrow && left - 44 >= topH;
        fit.chartH = left - (fit.top ? topH : 0);
        fit.chart = fit.chartH >= 30;
        fit.chartW = w;
        return fit;
    }

    private static RemoteViews views(Context context, int widgetId, FroglogWidgetArt.Box box, Fit fit,
            FroglogClient.Stats stats) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                context.getResources().getIdentifier("froglog_stats", "layout", context.getPackageName()));
        float e = fit.scale;
        int root = res(context, "froglog_stats_root");
        views.setViewPadding(root, FroglogWidgetArt.px(context, box.padH), FroglogWidgetArt.px(context, box.padV),
                FroglogWidgetArt.px(context, box.padH), FroglogWidgetArt.px(context, box.padV));
        FroglogTheme.Palette palette = FroglogTheme.resolve(context);
        FroglogWidgetTheme.accent(context, views, palette, "froglog_stats_title", "froglog_stats_streak",
                "froglog_stats_top_hours");
        FroglogWidgetTheme.ink(context, views, palette, "froglog_stats_month", "froglog_stats_message",
                "froglog_stats_week", "froglog_stats_year_hours", "froglog_stats_done", "froglog_stats_top_name");
        FroglogWidgetTheme.muted(context, views, palette, "froglog_stats_month_label", "froglog_stats_week_label",
                "froglog_stats_year_label", "froglog_stats_done_label", "froglog_stats_top_label");

        views.setViewVisibility(res(context, "froglog_stats_header"), fit.header ? View.VISIBLE : View.GONE);
        views.setViewVisibility(res(context, "froglog_stats_title"), fit.title ? View.VISIBLE : View.GONE);
        views.setTextViewText(res(context, "froglog_stats_title"), "Froglog");
        views.setTextViewTextSize(res(context, "froglog_stats_title"), TypedValue.COMPLEX_UNIT_SP,
                FroglogWidgetArt.clamp(12 * e, 10, 16));
        views.setViewVisibility(res(context, "froglog_stats_streak"), View.GONE);
        views.setOnClickPendingIntent(root, pod(context, widgetId, 0, FroglogGames.FILTER_RECENT));

        FroglogStatsSummary summary = stats == null ? null : stats.summary;
        if (!FroglogStore.signedIn(context)) {
            return message(context, views, fit, "Sign in to Froglog");
        }
        if (stats != null && stats.error != null) {
            return message(context, views, fit, stats.error);
        }
        if (stats == null) {
            return message(context, views, fit, "No stats yet");
        }

        int month = res(context, "froglog_stats_month");
        views.setTextViewText(month, monthText(stats));
        views.setTextViewTextSize(month, TypedValue.COMPLEX_UNIT_SP, fit.big);
        views.setTextViewTextSize(res(context, "froglog_stats_month_label"), TypedValue.COMPLEX_UNIT_SP, fit.label);
        views.setInt(res(context, "froglog_stats_hero_block"), "setGravity",
                fit.centered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        views.setInt(res(context, "froglog_stats_hero"), "setGravity",
                fit.centered ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        views.setInt(res(context, "froglog_stats_body"), "setGravity",
                fit.centered ? Gravity.CENTER : Gravity.TOP);
        views.setViewVisibility(res(context, "froglog_stats_spacer"),
                fit.sideChart || fit.centered ? View.GONE : View.VISIBLE);

        int sideChart = res(context, "froglog_stats_chart_side");
        int chart = res(context, "froglog_stats_chart");
        views.setViewVisibility(sideChart, View.GONE);
        views.setViewVisibility(chart, View.GONE);
        views.setViewVisibility(res(context, "froglog_stats_figures"), View.GONE);
        views.setViewVisibility(res(context, "froglog_stats_top"), View.GONE);
        if (summary == null) {
            return views;
        }

        if (fit.header && fit.title && summary.streak > 1) {
            int streak = res(context, "froglog_stats_streak");
            views.setViewVisibility(streak, View.VISIBLE);
            views.setTextViewText(streak, summary.streak + "-day streak");
            views.setTextViewTextSize(streak, TypedValue.COMPLEX_UNIT_SP, FroglogWidgetArt.clamp(11 * e, 9, 14));
        }

        if (fit.sideChart || fit.chart) {
            int target = fit.sideChart ? sideChart : chart;
            Bitmap bars = FroglogWidgetArt.bars(context, summary.lastDays(fit.days), summary.lastDayLetters(fit.days),
                    fit.chartW, fit.chartH, e, palette);
            views.setViewVisibility(target, View.VISIBLE);
            views.setImageViewBitmap(target, bars);
            views.setContentDescription(target, FroglogGames.duration(summary.weekHours) + " in the last 7 days");
            views.setOnClickPendingIntent(target, pod(context, widgetId, 4, FroglogGames.FILTER_RECENT));
        }

        if (fit.figures > 0) {
            views.setViewVisibility(res(context, "froglog_stats_figures"), View.VISIBLE);
            views.setTextViewText(res(context, "froglog_stats_week"), FroglogGames.duration(summary.weekHours));
            views.setTextViewText(res(context, "froglog_stats_year_hours"), FroglogGames.duration(summary.yearHours));
            views.setTextViewText(res(context, "froglog_stats_done"), String.valueOf(summary.yearCompleted));
            float value = FroglogWidgetArt.clamp(13 * e, 11, 17);
            float small = FroglogWidgetArt.clamp(9 * e, 8, 12);
            for (int i = 0; i < FIGURES.length; i++) {
                String key = FIGURES[i];
                views.setViewVisibility(res(context, "froglog_stats_" + key + "_box"),
                        i < fit.figures ? View.VISIBLE : View.GONE);
                views.setTextViewTextSize(res(context, "froglog_stats_" + key + "_label"),
                        TypedValue.COMPLEX_UNIT_SP, small);
            }
            views.setTextViewTextSize(res(context, "froglog_stats_week"), TypedValue.COMPLEX_UNIT_SP, value);
            views.setTextViewTextSize(res(context, "froglog_stats_year_hours"), TypedValue.COMPLEX_UNIT_SP, value);
            views.setTextViewTextSize(res(context, "froglog_stats_done"), TypedValue.COMPLEX_UNIT_SP, value);
            views.setOnClickPendingIntent(res(context, "froglog_stats_week_box"),
                    pod(context, widgetId, 1, FroglogGames.FILTER_RECENT));
            views.setOnClickPendingIntent(res(context, "froglog_stats_year_box"),
                    pod(context, widgetId, 2, FroglogGames.FILTER_PROGRESS));
            views.setOnClickPendingIntent(res(context, "froglog_stats_done_box"),
                    pod(context, widgetId, 3, FroglogGames.FILTER_COMPLETED));
        }

        if (fit.top && summary.topTitle != null) {
            int top = res(context, "froglog_stats_top");
            views.setViewVisibility(top, View.VISIBLE);
            views.setTextViewText(res(context, "froglog_stats_top_name"), summary.topTitle);
            views.setTextViewText(res(context, "froglog_stats_top_hours"), FroglogGames.duration(summary.topHours));
            Bitmap source = FroglogImages.fetch(summary.topCover, FroglogWidgetArt.px(context, fit.cover * 2));
            views.setImageViewBitmap(res(context, "froglog_stats_top_art"), FroglogWidgetArt.cover(context, source,
                    fit.cover, FroglogWidgetArt.withAlpha(palette.muted, 0x33)));
            if (summary.topId > 0) {
                FroglogGame game = new FroglogGame(summary.topId, summary.topLive, summary.topTitle, null,
                        summary.topCover, null, null, null, 0,
                        FroglogGames.duration(summary.topHours) + " this month", 0L);
                Intent detail = FroglogGameDetail.intent(context, game, true);
                detail.setData(Uri.parse("froglog://stats-top/" + widgetId));
                views.setOnClickPendingIntent(top, PendingIntent.getActivity(context, widgetId * 10 + 5,
                        detail, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        }
        return views;
    }

    private static String monthText(FroglogClient.Stats stats) {
        if (stats == null) {
            return "0h";
        }
        return stats.summary == null ? stats.compactHours : FroglogGames.duration(stats.summary.monthHours);
    }

    private static RemoteViews message(Context context, RemoteViews views, Fit fit, String text) {
        views.setViewVisibility(res(context, "froglog_stats_body"), View.GONE);
        views.setViewVisibility(res(context, "froglog_stats_header"), View.GONE);
        views.setViewVisibility(res(context, "froglog_stats_message_box"), View.VISIBLE);
        views.setViewVisibility(res(context, "froglog_stats_message_icon"), fit.centered ? View.GONE : View.VISIBLE);
        int message = res(context, "froglog_stats_message");
        views.setTextViewText(message, text);
        views.setTextViewTextSize(message, TypedValue.COMPLEX_UNIT_SP, FroglogWidgetArt.clamp(11 * fit.scale, 9, 14));
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

    private static int res(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }
}
