package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds "Log to Froglog…" to the game context menu. The menu list comes from a8.z.E and its
 * actions are dispatched by kf.n2.Z0 and lf.k.g, which both call {@link #onAction} first.
 */
public final class FroglogMenu {
    public static final String ACTION = "log_to_froglog";
    private static final String TAG = "FroglogMenu";
    private static final Set<String> CLOSING = new HashSet<String>(Arrays.asList(
            "hide_game", "remove_from_home", "delete_shortcut", "remove_shortcut_from_folder"));

    private static volatile String title;
    private static volatile String platform;

    private FroglogMenu() {}

    /** Called with every menu Cocoon builds. Only the game context menu gets the extra row. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List withFroglog(List items, Object menu, Object target) {
        if (items == null || !(menu instanceof Enum) || !"CONTEXT_MENU".equals(((Enum) menu).name())) {
            return items;
        }
        try {
            Object game = game(target);
            if (game == null) {
                return items;
            }
            String name = text(game, "getTitle");
            if (name.isEmpty()) {
                name = text(game, "getDisplayName");
            }
            if (name.isEmpty()) {
                return items;
            }
            title = name;
            platform = text(game, "getPlatformId");
            int insert = items.size();
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if (!(item instanceof nf.g)) {
                    continue;
                }
                String action = ((nf.g) item).c;
                if (ACTION.equals(action)) {
                    return items;
                }
                if (insert == items.size() && action != null && CLOSING.contains(action)) {
                    insert = i;
                }
            }
            ArrayList out = new ArrayList(items);
            out.add(insert, new nf.g(ef.b.FROGLOG, "Log to Froglog\u2026", ACTION, false, false, null, 0x1f8));
            return out;
        } catch (Throwable error) {
            Log.w(TAG, "menu row skipped", error);
            return items;
        }
    }

    /** True when the action was Froglog's, so Cocoon's dispatcher returns without handling it. */
    public static boolean onAction(Context context, String action) {
        if (!ACTION.equals(action)) {
            return false;
        }
        try {
            de.f1.O();
        } catch (Throwable ignored) {
        }
        if (context == null) {
            return true;
        }
        String name = title;
        Intent intent;
        if (!FroglogStore.signedIn(context)) {
            Toast.makeText(context, "Sign in to Froglog first", Toast.LENGTH_SHORT).show();
            intent = new Intent(context, FroglogPodActivity.class);
        } else if (name == null || name.isEmpty()) {
            intent = new Intent(context, FroglogMappingsActivity.class);
        } else {
            intent = new Intent(context, FroglogMappingsActivity.class)
                    .putExtra(FroglogMappingsActivity.EXTRA_TITLE, name)
                    .putExtra(FroglogMappingsActivity.EXTRA_PLATFORM, platform == null ? "" : platform);
        }
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (RuntimeException error) {
            Log.w(TAG, "could not open Froglog", error);
        }
        return true;
    }

    /** de.k is the game tile. Folders, widgets, and shortcuts use other de.o subclasses. */
    private static Object game(Object target) throws ReflectiveOperationException {
        if (target == null || !"de.k".equals(target.getClass().getName())) {
            return null;
        }
        Field field = target.getClass().getDeclaredField("a");
        field.setAccessible(true);
        Object game = field.get(target);
        if (game == null || !"rip.moth.cocoonshell.data.model.Game".equals(game.getClass().getName())) {
            return null;
        }
        return game;
    }

    private static String text(Object game, String getter) {
        try {
            Method method = game.getClass().getMethod(getter);
            Object value = method.invoke(game);
            return value == null ? "" : value.toString().trim();
        } catch (ReflectiveOperationException error) {
            return "";
        }
    }
}
