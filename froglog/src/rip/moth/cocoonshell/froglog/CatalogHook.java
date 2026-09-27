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
 * Adds a Froglog tile to Cocoon's widget catalog.
 * The existing Recently played tile ({@code NOW_PLAYING}) is left as it is.
 * Froglog is a separate catalog entry that hosts this app's own widget.
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
                Log.i(TAG, "Added Froglog to the widget catalog");
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
            int label = res("string", "widget_type_froglog");
            int desc = res("string", "widget_type_froglog_desc");
            if (label == 0 || desc == 0 || containsLabel(existing, label)) {
                return existing;
            }
            ArrayList<Object> copy = new ArrayList<Object>(existing);
            ArrayList<ta.j> sizes = new ArrayList<ta.j>();
            sizes.add(new ta.j(Integer.valueOf(4), Integer.valueOf(2)));
            copy.add(new mf.o1(
                    rip.moth.cocoonshell.data.model.Widget.WidgetType.ANDROID_WIDGET,
                    label,
                    desc,
                    ef.b.GAMEPAD,
                    sizes));
            return copy;
        } catch (Throwable t) {
            Log.e(TAG, "Catalog append failed", t);
            return existing;
        }
    }

    /**
     * Picker hook. Returns true only for the Froglog tile, which is then placed
     * immediately. Every other tile, including Recently played and Android widget,
     * keeps the original path.
     */
    public static boolean maybeConfirm(int labelRes, jb.g callback) {
        try {
            if (context == null || callback == null || labelRes == 0) {
                return false;
            }
            if (labelRes != res("string", "widget_type_froglog")) {
                return false;
            }
            AppWidgetProviderInfo info = provider();
            if (info == null) {
                return false;
            }
            ArrayList<ta.j> sizes = new ArrayList<ta.j>();
            sizes.add(new ta.j(Integer.valueOf(4), Integer.valueOf(2)));
            mf.y1.f(callback, rip.moth.cocoonshell.data.model.Widget.WidgetType.ANDROID_WIDGET, info, sizes);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Could not place Froglog widget", t);
            return false;
        }
    }

    private static boolean containsLabel(List<?> existing, int label) {
        for (Object item : existing) {
            if (item instanceof mf.o1 && ((mf.o1) item).b == label) {
                return true;
            }
        }
        return false;
    }

    private static AppWidgetProviderInfo provider() {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        String name = FroglogRecentWidget.class.getName();
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
