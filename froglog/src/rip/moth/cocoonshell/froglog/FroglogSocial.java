package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Puts live Froglog follows on Cocoon's own Froglog friends tab.
 * Last-played people stay off the social list. Taps open FroglogFriendActivity.
 */
public final class FroglogSocial {
    private static final String TAG = "FroglogWidget";
    private static final long FRESH_MS = 60000L;

    private static volatile List<FroglogFollow> cache = Collections.emptyList();
    private static volatile int cacheVersion;
    private static volatile int rowVersion = -1;
    private static volatile List<?> rowCache = Collections.emptyList();
    private static volatile long fetchedAt;
    private static volatile boolean loading;
    private static yb.z0 liveFlow;
    private static int lastLogged = -1;
    private static final long ART_MS = 10L * 60L * 1000L;
    private static final Map<String, String> artCache = new HashMap<String, String>();
    private static final Map<String, Long> artAt = new HashMap<String, Long>();

    private FroglogSocial() {}

    private static synchronized yb.z0 liveFlow() {
        if (liveFlow == null) {
            liveFlow = yb.r0.c(Integer.valueOf(liveCount(cache)));
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
        y0.v0 state = lb.a.w(liveFlow(), Integer.valueOf(0), (y0.e0) composer, 0);
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
        cacheVersion++;
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
            if (tabs.get(i) == hf.u1.FROGLOG) {
                return tabs;
            }
        }
        ArrayList<Object> next = new ArrayList<Object>(tabs.size() + 1);
        next.add(hf.u1.FROGLOG);
        next.addAll(tabs);
        return next;
    }

    /** The selected social tab's friend rows. Froglog is never mixed into Steam. */
    public static List<?> listForTab(Object tab, List<?> existing) {
        refreshSoon(token(), self());
        if (tab != hf.u1.FROGLOG) {
            return existing;
        }
        if (rowVersion == cacheVersion) {
            return rowCache;
        }
        ArrayList<Object> live = new ArrayList<Object>();
        List<FroglogFollow> people = cache;
        for (int i = 0; i < people.size(); i++) {
            FroglogFollow person = people.get(i);
            if (person.playing) {
                live.add(row(person));
            }
        }
        rowCache = live;
        rowVersion = cacheVersion;
        Log.i(TAG, "Froglog tab " + live.size() + " now-playing");
        return live;
    }

    /** Keep the Froglog chip from using the Steam glyph. */
    public static Object tabIcon(Object tab, Object icon) {
        if (tab == hf.u1.FROGLOG) {
            return hf.b.FROGLOG;
        }
        return icon;
    }

    /** Steam chat calls longValue on a friend id. Froglog rows have none, so open them here. */
    public static boolean openIfFriend(Object friend) {
        try {
            if (friend == null || !"hf.da".equals(friend.getClass().getName())) {
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
            List<FroglogFollow> people = withArtwork(token, FroglogFollows.people(activity, online, following, self));
            cache = FroglogAvatars.localize(CatalogHook.context, people);
            cacheVersion++;
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

    private static hf.da row(FroglogFollow person) {
        // Froglog presence is in-game only. Last-played never reaches this list.
        String art = person.artwork;
        if (art != null && art.isEmpty()) {
            art = null;
        }
        return new hf.da(
                "froglog:" + person.username,
                null,
                person.name,
                person.avatarUrl,
                hf.t1.IN_GAME,
                person.status,
                person.game,
                "froglog",
                art,
                false);
    }

    /** Fills in the game background when the online row did not carry one. */
    private static List<FroglogFollow> withArtwork(String token, List<FroglogFollow> people) {
        if (people == null || people.isEmpty()) {
            return people;
        }
        ArrayList<FroglogFollow> out = new ArrayList<FroglogFollow>(people.size());
        for (int i = 0; i < people.size(); i++) {
            FroglogFollow person = people.get(i);
            if (!person.playing || person.artwork != null || person.game == null || person.game.isEmpty()) {
                out.add(person);
                continue;
            }
            String key = person.username.toLowerCase(Locale.ROOT) + "\n" + FroglogMatch.normalizeTitle(person.game);
            String art = freshArt(key);
            if (art == null) {
                art = fetchArt(token, person.username, person.game);
                synchronized (artCache) {
                    artCache.put(key, art == null ? "" : art);
                    artAt.put(key, Long.valueOf(System.currentTimeMillis()));
                }
            }
            if (art == null || art.isEmpty()) {
                out.add(person);
            } else {
                out.add(new FroglogFollow(person.username, person.name, person.avatarUrl, person.status,
                        person.game, person.playing, art));
            }
        }
        return out;
    }

    private static String freshArt(String key) {
        synchronized (artCache) {
            Long at = artAt.get(key);
            if (at == null || System.currentTimeMillis() - at.longValue() > ART_MS) {
                return null;
            }
            return artCache.containsKey(key) ? artCache.get(key) : null;
        }
    }

    private static String fetchArt(String token, String username, String title) {
        try {
            FroglogClient.Recent recent = FroglogClient.recentGames(token, username, 80, FroglogGames.FILTER_RECENT);
            if (recent == null || recent.games == null) {
                return null;
            }
            String wanted = FroglogMatch.normalizeTitle(title);
            for (int i = 0; i < recent.games.size(); i++) {
                FroglogGame game = recent.games.get(i);
                if (game != null && wanted.equals(FroglogMatch.normalizeTitle(game.title))) {
                    return FroglogFollows.wideArt(game.json, game.coverUrl);
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not load Froglog game art for " + username);
        }
        return null;
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
