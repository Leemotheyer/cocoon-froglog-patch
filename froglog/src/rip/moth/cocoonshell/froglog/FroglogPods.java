package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Adds the Froglog pod beside Picnic, Log, and the other Cocoon pods. */
public final class FroglogPods {
    private static final String TAG = "FroglogWidget";
    static final String SLUG = "froglog";
    static final String URI = "cocoon://pod/froglog";

    private FroglogPods() {}

    public static void install(Context appContext) {
        CatalogHook.context = appContext.getApplicationContext();
        try {
            Class<?> pods = Class.forName("xd.m0");
            Field field = pods.getDeclaredField("a");
            field.setAccessible(true);
            Object current = field.get(null);
            if (!(current instanceof List)) {
                return;
            }
            List<?> existing = (List<?>) current;
            List<?> next = include(existing);
            if (next != existing) {
                setStatic(field, next);
                Log.i(TAG, "Added the Froglog pod");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not add the Froglog pod", t);
        }
    }

    /** Called from the pod list initializer, after the original pods are built. */
    public static List<?> include(List<?> existing) {
        try {
            if (CatalogHook.context == null || existing == null || contains(existing)) {
                return existing;
            }
            xd.l0 entry = entry(CatalogHook.context);
            if (entry == null) {
                return existing;
            }
            ArrayList<Object> copy = new ArrayList<Object>(existing);
            copy.add(entry);
            return copy;
        } catch (Throwable t) {
            Log.e(TAG, "Pod append failed", t);
            return existing;
        }
    }

    /** Opens the Froglog pod and skips Cocoon's normal pod router. */
    public static boolean openIfFroglog(xd.l0 entry, Context context) {
        try {
            if (entry == null || context == null || !isFroglog(entry)) {
                return false;
            }
            Intent open = new Intent(context, FroglogPodActivity.class);
            if (!(context instanceof Activity)) {
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(open);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Could not open the Froglog pod", t);
            return false;
        }
    }

    private static boolean contains(List<?> existing) {
        for (Object item : existing) {
            if (item instanceof xd.l0 && isFroglog((xd.l0) item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFroglog(xd.l0 entry) {
        return SLUG.equals(entry.g) || URI.equals(entry.b);
    }

    private static xd.l0 entry(Context context) {
        int icon = context.getResources().getIdentifier("froglog", "drawable", context.getPackageName());
        int label = context.getResources().getIdentifier("pods_overlay_froglog", "string", context.getPackageName());
        if (icon == 0) {
            return null;
        }
        return new xd.l0(
                xd.k0.SETTINGS,
                URI,
                "Froglog",
                "Froglog",
                "Sign in, see your library, and log the games you play in Cocoon.",
                icon,
                SLUG,
                icon,
                icon,
                Collections.singletonList(Integer.valueOf(icon)),
                label);
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
