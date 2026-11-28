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
            Class<?> pods = Class.forName("yd.l0");
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
            syncOverlay(pods, next);
        } catch (Throwable t) {
            Log.e(TAG, "Could not add the Froglog pod", t);
        }
    }

    /**
     * The overlay keeps its own copy of the pod list (yd.l0.b, aliased by pf.v.a). It is
     * built in the same class initializer as yd.l0.a, so if the catalog is initialized
     * before the app context is available, include() cannot add the pod and the overlay
     * copy is left without it. Add it to that live list too.
     */
    private static void syncOverlay(Class<?> pods, List<?> catalog) {
        try {
            Object found = null;
            for (Object item : catalog) {
                if (item instanceof yd.k0 && isFroglog((yd.k0) item)) {
                    found = item;
                    break;
                }
            }
            if (found == null) {
                found = entry(CatalogHook.context);
            }
            if (found == null) {
                return;
            }
            Field field = pods.getDeclaredField("b");
            field.setAccessible(true);
            Object current = field.get(null);
            if (!(current instanceof List)) {
                return;
            }
            List<Object> overlay = (List<Object>) current;
            if (!contains(overlay)) {
                overlay.add(found);
                Log.i(TAG, "Added the Froglog pod to the overlay");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not add the Froglog pod to the overlay", t);
        }
    }

    /** Called from the pod list initializer, after the original pods are built. */
    public static List<?> include(List<?> existing) {
        try {
            if (CatalogHook.context == null || existing == null || contains(existing)) {
                return existing;
            }
            yd.k0 entry = entry(CatalogHook.context);
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
    public static boolean openIfFroglog(yd.k0 entry, Context context) {
        try {
            if (entry == null || context == null || !isFroglog(entry)) {
                return false;
            }
            return launch(context);
        } catch (Throwable t) {
            Log.e(TAG, "Could not open the Froglog pod", t);
            return false;
        }
    }

    /**
     * The overlay click sends the pod {@code action} type, not the entry.
     * Froglog must use its own type so this does not open Log or Settings.
     */
    public static boolean openIfAction(yd.j0 action, Context context) {
        try {
            if (action != yd.j0.FROGLOG || context == null) {
                return false;
            }
            return launch(context);
        } catch (Throwable t) {
            Log.e(TAG, "Could not open the Froglog pod", t);
            return false;
        }
    }

    private static boolean launch(Context context) {
        Intent open = new Intent(context, FroglogPodActivity.class);
        if (!(context instanceof Activity)) {
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(open);
        return true;
    }

    private static boolean contains(List<?> existing) {
        for (Object item : existing) {
            if (item instanceof yd.k0 && isFroglog((yd.k0) item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFroglog(yd.k0 entry) {
        return SLUG.equals(entry.g) || URI.equals(entry.b);
    }

    private static yd.k0 entry(Context context) {
        int icon = context.getResources().getIdentifier("froglog", "drawable", context.getPackageName());
        int label = context.getResources().getIdentifier("pods_overlay_froglog", "string", context.getPackageName());
        if (icon == 0) {
            return null;
        }
        return new yd.k0(
                yd.j0.FROGLOG,
                URI,
                "Froglog",
                "Froglog",
                "Sign in, see your library, and log the games you play in Cocoon.",
                icon,
                SLUG,
                icon,
                icon,
                Collections.nCopies(4, Integer.valueOf(icon)),
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
