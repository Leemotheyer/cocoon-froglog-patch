package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Puts live Froglog follows on Cocoon's own Froglog friends tab.
 * Last-played people stay off the social list. Taps open FroglogFriendActivity.
 */
public final class FroglogSocial {
    private static final String TAG = "FroglogWidget";
    private static final long FRESH_MS = 60000L;

    private static volatile List<FroglogFollow> cache = Collections.emptyList();
    private static volatile long fetchedAt;
    private static volatile boolean loading;
    private static yb.v0 liveFlow;
    private static int lastLogged = -1;

    private FroglogSocial() {}

    private static synchronized yb.v0 liveFlow() {
        if (liveFlow == null) {
            liveFlow = yb.n0.c(Integer.valueOf(liveCount(cache)));
        }
        return liveFlow;
    }

    private static int liveCount(List<FroglogFollow> people) {
        int live = 0;
        for (int i = 0; i < people.size(); i++) {
            if (people.get(i).playing) {
                live++;
            }
        }
        return live;
    }

    private static void publish() {
        try {
            liveFlow().h(Integer.valueOf(liveCount(cache)));
        } catch (Throwable t) {
            Log.w(TAG, "Could not publish the Froglog friend count", t);
        }
    }

    /**
     * Called by the status-bar friends pill right after Cocoon counts Steam friends.
     * Must run on every composition so the pill's group structure stays stable.
     */
    public static int withLiveCount(int steam, Object composer) {
        refreshSoon(token(), self());
        z0.u0 state = z7.h0.Q(liveFlow(), (z0.e0) composer);
        Object value = state.getValue();
        int live = token() != null && value instanceof Integer ? ((Integer) value).intValue() : 0;
        if (live != lastLogged) {
            lastLogged = live;
            Log.i(TAG, "Status bar friends: " + steam + " Steam + " + live + " Froglog");
        }
        return steam + live;
    }

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
        publish();
    }

    /** Steam conversion stays Steam-only. Kept so an old hook is harmless. */
    public static List<?> withFollows(List<?> existing) {
        refreshSoon(token(), self());
        return existing;
    }

    /** Cocoon draws the friends panel only when Steam is available. A Froglog sign-in counts too. */
    public static boolean showFriendsPanel(boolean steam) {
        return steam || (token() != null && !token().isEmpty());
    }

    /** Adds a Froglog tab beside Steam and Android when the user is signed in. */
    public static List<?> withFriendsTabs(List<?> tabs) {
        refreshSoon(token(), self());
        if (tabs == null) {
            return tabs;
        }
        if (token() == null || token().isEmpty()) {
            return tabs;
        }
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i) == ef.w0.FROGLOG) {
                return tabs;
            }
        }
        ArrayList<Object> next = new ArrayList<Object>(tabs.size() + 1);
        next.add(ef.w0.FROGLOG);
        next.addAll(tabs);
        return next;
    }

    /** The selected social tab's friend rows. Froglog is never mixed into Steam. */
    public static List<?> listForTab(Object tab, List<?> existing) {
        refreshSoon(token(), self());
        if (tab != ef.w0.FROGLOG) {
            return existing;
        }
        ArrayList<Object> live = new ArrayList<Object>();
        List<FroglogFollow> people = cache;
        for (int i = 0; i < people.size(); i++) {
            FroglogFollow person = people.get(i);
            if (person.playing) {
                live.add(row(person));
            }
        }
        Log.i(TAG, "Froglog tab " + live.size() + " now-playing");
        return live;
    }

    /** Keep the Froglog chip from using the Steam glyph. */
    public static Object tabIcon(Object tab, Object icon) {
        if (tab == ef.w0.FROGLOG) {
            return ef.b.FROGLOG;
        }
        return icon;
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
            String following = FroglogClient.followingJson(token);
            if (following == null) {
                Log.w(TAG, "Froglog follow list unavailable, using the activity feed");
            }
            List<FroglogFollow> people = FroglogFollows.people(activity, online, following, self);
            cache = FroglogAvatars.localize(CatalogHook.context, people);
            int live = 0;
            for (int i = 0; i < cache.size(); i++) {
                if (cache.get(i).playing) {
                    live++;
                }
            }
            Log.i(TAG, "Froglog follows " + cache.size() + ", " + live + " in game");
            publish();
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
        // Froglog presence is in-game only. Last-played never reaches this list.
        return new ef.d6(
                "froglog:" + person.username,
                null,
                person.name,
                person.avatarUrl,
                ef.v0.IN_GAME,
                person.status,
                person.game,
                "froglog",
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
