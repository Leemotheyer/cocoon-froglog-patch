package rip.moth.cocoonshell.froglog;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds Froglog tiles to Cocoon's widget catalog.
 * The existing Recently played tile ({@code NOW_PLAYING}) is left as it is.
 * Froglog recent games and Froglog stats are separate catalog entries.
 */
public final class CatalogHook {
    private static final String TAG = "FroglogWidget";
    static Context context;

    private CatalogHook() {}

    public static void install(Context appContext) {
        context = appContext.getApplicationContext();
        try {
            Class<?> catalog = Class.forName("mf.y1");
            Field field = catalog.getDeclaredField("f");
            field.setAccessible(true);
            Object current = field.get(null);
            if (!(current instanceof List)) {
                return;
            }
            List<?> existing = (List<?>) current;
            List<?> next = withFroglog(existing);
            if (next != existing) {
                setStatic(field, next);
                Log.i(TAG, "Added Froglog tiles to the widget catalog");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not add Froglog to the widget catalog", t);
        }
    }

    /** Called from the catalog class initializer, after the original list is built. */
    public static List<?> withFroglog(List<?> existing) {
        try {
            if (context == null || existing == null) {
                return existing;
            }
            ArrayList<Object> copy = append(null, existing,
                    "widget_type_froglog", "widget_type_froglog_desc", ef.b.GAMEPAD, 3, 2);
            copy = append(copy, existing,
                    "widget_type_froglog_stats", "widget_type_froglog_stats_desc", ef.b.BAR_GRAPH, 1, 1);
            return copy == null ? existing : copy;
        } catch (Throwable t) {
            Log.e(TAG, "Catalog append failed", t);
            return existing;
        }
    }

    /**
     * Picker hook. Returns true only for a Froglog tile, which is then placed
     * immediately. Every other tile, including Recently played and Android widget,
     * keeps the original path.
     */
    public static boolean maybeConfirm(int labelRes, jb.g callback) {
        try {
            if (context == null || callback == null || labelRes == 0) {
                return false;
            }
            String provider;
            int width;
            int height;
            if (labelRes == res("string", "widget_type_froglog")) {
                provider = FroglogRecentWidget.class.getName();
                width = 3;
                height = 2;
            } else if (labelRes == res("string", "widget_type_froglog_stats")) {
                provider = FroglogStatsWidget.class.getName();
                width = 1;
                height = 1;
            } else {
                return false;
            }
            AppWidgetProviderInfo info = provider(provider);
            if (info == null) {
                return false;
            }
            ArrayList<ta.j> sizes = new ArrayList<ta.j>();
            sizes.add(new ta.j(Integer.valueOf(width), Integer.valueOf(height)));
            mf.y1.f(callback, rip.moth.cocoonshell.data.model.Widget.WidgetType.ANDROID_WIDGET, info, sizes);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Could not place Froglog widget", t);
            return false;
        }
    }

    private static ArrayList<Object> append(ArrayList<Object> copy, List<?> existing,
            String labelName, String descName, ef.b icon, int width, int height) {
        int label = res("string", labelName);
        int desc = res("string", descName);
        if (label == 0 || desc == 0) {
            return copy;
        }
        List<?> look = copy == null ? existing : copy;
        if (containsLabel(look, label)) {
            return copy;
        }
        if (copy == null) {
            copy = new ArrayList<Object>(existing);
        }
        ArrayList<ta.j> sizes = new ArrayList<ta.j>();
        sizes.add(new ta.j(Integer.valueOf(width), Integer.valueOf(height)));
        copy.add(new mf.o1(
                rip.moth.cocoonshell.data.model.Widget.WidgetType.ANDROID_WIDGET,
                label,
                desc,
                icon,
                sizes));
        return copy;
    }

    private static boolean containsLabel(List<?> existing, int label) {
        for (Object item : existing) {
            if (item instanceof mf.o1 && ((mf.o1) item).b == label) {
                return true;
            }
        }
        return false;
    }

    private static AppWidgetProviderInfo provider(String name) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (AppWidgetProviderInfo info : manager.getInstalledProviders()) {
            if (info.provider != null && name.equals(info.provider.getClassName())) {
                return info;
            }
        }
        return null;
    }

    private static int res(String type, String name) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    private static void setStatic(Field field, Object value) throws Exception {
        field.setAccessible(true);
        try {
            Field accessFlags = Field.class.getDeclaredField("accessFlags");
            accessFlags.setAccessible(true);
            accessFlags.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        }
        field.set(null, value);
    }
}
