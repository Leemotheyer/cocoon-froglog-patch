package rip.moth.cocoonshell.froglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

/** Hours and finished games from GET /api/stats. Separate from Recently played. */
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
                    FroglogClient.Stats stats = null;
                    if (FroglogStore.signedIn(app)) {
                        stats = FroglogClient.stats(FroglogStore.token(app));
                    }
                    AppWidgetManager widgets = AppWidgetManager.getInstance(app);
                    for (int id : ids) {
                        widgets.updateAppWidget(id, views(app, id, stats));
                    }
                } finally {
                    pending.finish();
                }
            }
        }, "froglog-stats").start();
    }

    private static RemoteViews views(Context context, int widgetId, FroglogClient.Stats stats) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                context.getResources().getIdentifier("froglog_stats", "layout", context.getPackageName()));
        int title = res(context, "froglog_stats_title");
        int month = res(context, "froglog_stats_month");
        int year = res(context, "froglog_stats_year");
        int rate = res(context, "froglog_stats_rate");
        int message = res(context, "froglog_stats_message");
        views.setTextViewText(title, "Froglog");
        views.setViewVisibility(rate, View.GONE);
        if (!FroglogStore.signedIn(context)) {
            show(views, month, year, rate, message, "Sign in");
        } else if (stats != null && stats.error != null) {
            show(views, month, year, rate, message, stats.error);
        } else if (stats == null) {
            show(views, month, year, rate, message, "No stats");
        } else {
            views.setViewVisibility(message, View.GONE);
            views.setViewVisibility(month, View.VISIBLE);
            views.setViewVisibility(year, View.VISIBLE);
            views.setTextViewText(month, stats.compactHours);
            views.setTextViewText(year, "this month");
        }
        Intent open = new Intent(context, FroglogPodActivity.class);
        open.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        open.setData(Uri.parse("froglog://stats/" + widgetId));
        views.setOnClickPendingIntent(res(context, "froglog_stats_root"), PendingIntent.getActivity(context, widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    private static void show(RemoteViews views, int month, int year, int rate, int message, String text) {
        views.setViewVisibility(month, View.GONE);
        views.setViewVisibility(year, View.GONE);
        views.setViewVisibility(rate, View.GONE);
        views.setViewVisibility(message, View.VISIBLE);
        views.setTextViewText(message, text);
    }

    private static int res(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }
}
