package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Puts Froglog follows into Cocoon's friends list and opens their games on tap.
 * The password is never involved. The list comes from the signed-in activity feed.
 */
public final class FroglogSocial {
    private static final String TAG = "FroglogWidget";
    private static final long FRESH_MS = 60000L;

    private static volatile List<FroglogFollow> cache = Collections.emptyList();
    private static volatile long fetchedAt;
    private static volatile boolean loading;

    private FroglogSocial() {}

    public static void warm(Context context) {
        if (context == null || !FroglogStore.signedIn(context)) {
            return;
        }
        refreshSoon(FroglogStore.token(context), FroglogStore.username(context));
    }

    public static List<FroglogFollow> snapshot() {
        return cache;
    }

    public static void clear() {
        cache = Collections.emptyList();
        fetchedAt = 0L;
    }

    /** Called at the end of Cocoon's Steam friend conversion. Must stay off the network. */
    public static List<?> withFollows(List<?> existing) {
        refreshSoon(token(), self());
        List<FroglogFollow> people = cache;
        if (existing == null || people.isEmpty()) {
            return existing;
        }
        ArrayList<Object> merged = new ArrayList<Object>(people.size() + existing.size());
        for (FroglogFollow person : people) {
            merged.add(row(person));
        }
        merged.addAll(existing);
        return merged;
    }

    /** Steam chat calls longValue on a friend id. Froglog rows have none, so open them here. */
    public static boolean openIfFriend(Object friend) {
        try {
            if (friend == null || !"ef.d6".equals(friend.getClass().getName())) {
                return false;
            }
            String key = stringField(friend, "a");
            if (key == null || !key.startsWith("froglog:")) {
                return false;
            }
            Context context = CatalogHook.context;
            if (context == null) {
                return true;
            }
            Intent open = new Intent(context, FroglogFriendActivity.class);
            open.putExtra(FroglogFriendActivity.EXTRA_USERNAME, key.substring("froglog:".length()));
            open.putExtra(FroglogFriendActivity.EXTRA_NAME, stringField(friend, "c"));
            open.putExtra(FroglogFriendActivity.EXTRA_STATUS, stringField(friend, "f"));
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(open);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Could not open a Froglog follow", t);
            return true;
        }
    }

    public static void loadIntoCache(String token, String self) {
        if (token == null || token.isEmpty()) {
            clear();
            return;
        }
        try {
            String activity = FroglogClient.activityJson(token);
            String online = FroglogClient.onlineJson(token);
            cache = FroglogFollows.people(activity, online, self);
        } catch (Exception e) {
            Log.w(TAG, "Could not load Froglog follows");
        } finally {
            fetchedAt = System.currentTimeMillis();
        }
    }

    private static void refreshSoon(final String token, final String self) {
        if (token == null || token.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (loading || now - fetchedAt < FRESH_MS) {
            return;
        }
        synchronized (FroglogSocial.class) {
            if (loading || System.currentTimeMillis() - fetchedAt < FRESH_MS) {
                return;
            }
            loading = true;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    loadIntoCache(token, self);
                } finally {
                    loading = false;
                }
            }
        }, "froglog-follows").start();
    }

    private static ef.d6 row(FroglogFollow person) {
        ef.v0 tier = person.playing || person.game != null ? ef.v0.IN_GAME : ef.v0.ONLINE;
        return new ef.d6(
                "froglog:" + person.username,
                null,
                person.name,
                person.avatarUrl,
                tier,
                person.status,
                person.game,
                null,
                null,
                false);
    }

    private static String token() {
        Context context = CatalogHook.context;
        return context == null ? null : FroglogStore.token(context);
    }

    private static String self() {
        Context context = CatalogHook.context;
        return context == null ? null : FroglogStore.username(context);
    }

    private static String stringField(Object friend, String name) throws Exception {
        Field field = friend.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(friend);
        return value == null ? "" : String.valueOf(value);
    }
}
