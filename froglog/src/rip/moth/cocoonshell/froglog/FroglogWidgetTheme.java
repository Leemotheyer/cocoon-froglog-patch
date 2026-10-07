package rip.moth.cocoonshell.froglog;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

/** Colours RemoteViews text from the Cocoon theme and redraws widgets when it changes. */
public final class FroglogWidgetTheme {
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private FroglogWidgetTheme() {}

    public static void ink(Context context, RemoteViews views, FroglogTheme.Palette p, String... ids) {
        color(context, views, p.ink, ids);
    }

    public static void muted(Context context, RemoteViews views, FroglogTheme.Palette p, String... ids) {
        color(context, views, p.muted, ids);
    }

    public static void accent(Context context, RemoteViews views, FroglogTheme.Palette p, String... ids) {
        color(context, views, p.accent, ids);
    }

    private static void color(Context context, RemoteViews views, int color, String... ids) {
        for (String name : ids) {
            int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
            if (id != 0) {
                views.setTextColor(id, color);
            }
        }
    }

    /** The prefs listener is held strongly; SharedPreferences only keeps a weak reference. */
    public static synchronized void watch(Context context) {
        if (listener != null) {
            return;
        }
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final Runnable redraw = new Runnable() {
            @Override
            public void run() {
                refresh(app);
            }
        };
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (key == null || key.startsWith("theme_") || key.equals("accent_theme")
                        || key.equals("custom_theme_colors_id")) {
                    main.removeCallbacks(redraw);
                    main.postDelayed(redraw, 600);
                }
            }
        };
        app.getSharedPreferences(FroglogTheme.PREFS, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(listener);
    }

    public static void refresh(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] recent = manager.getAppWidgetIds(new ComponentName(context, FroglogRecentWidget.class));
            if (recent.length > 0) {
                new FroglogRecentWidget().onUpdate(context, manager, recent);
            }
            int[] stats = manager.getAppWidgetIds(new ComponentName(context, FroglogStatsWidget.class));
            if (stats.length > 0) {
                new FroglogStatsWidget().onUpdate(context, manager, stats);
            }
        } catch (RuntimeException ignored) {
            // Widget host not ready yet.
        }
    }
}
