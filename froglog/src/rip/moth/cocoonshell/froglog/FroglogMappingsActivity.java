package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Lists which Froglog game each Cocoon title logs to and lets the player change, remove, or
 * stop a mapping. A change applies to sessions that have not been sent yet.
 */
public class FroglogMappingsActivity extends FroglogActivity {
    private static final int ROWS = 60;

    private ScrollView scroll;
    private LinearLayout root;
    private TextView status;
    private LinearLayout rows;
    private List<FroglogGame> library = new ArrayList<FroglogGame>();
    private String libraryError;
    private boolean libraryLoaded;
    private final Map<String, String[]> names = new HashMap<String, String[]>();
    private final Map<String, String> platformNames = new HashMap<String, String>();
    private final List<CocoonLibrary.Game> cocoon = new ArrayList<CocoonLibrary.Game>();
    private String screen = "list";
    private String editTitle = "";
    private String editPlatform = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root);
        setContentView(scroll);
        FroglogTheme.paintSystemBars(this);
        readCocoon();
        showList();
        loadLibrary();
    }

    @Override
    public void onBackPressed() {
        if (!"list".equals(screen)) {
            showList();
            return;
        }
        super.onBackPressed();
    }

    private void readCocoon() {
        HashSet<String> seen = new HashSet<String>();
        List<CocoonLibrary.Game> games = CocoonLibrary.all(this);
        for (int i = 0; i < games.size(); i++) {
            CocoonLibrary.Game game = games.get(i);
            String platform = game.platformId == null ? "" : game.platformId;
            if (!platform.isEmpty() && game.platformName != null && !game.platformName.isEmpty()) {
                platformNames.put(platform, game.platformName);
            }
            String key = FroglogMatch.linkKey(game.title, platform);
            if (seen.add(key)) {
                names.put(key, new String[] {game.title, platform});
                cocoon.add(game);
            }
        }
    }

    private void loadLibrary() {
        if (!FroglogStore.signedIn(this)) {
            return;
        }
        final String token = FroglogStore.token(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FroglogClient.Recent loaded = FroglogClient.library(token);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        libraryLoaded = true;
                        libraryError = loaded.error;
                        library = loaded.games == null ? new ArrayList<FroglogGame>() : loaded.games;
                        if ("edit".equals(screen)) {
                            showEdit(editTitle, editPlatform);
                        } else if ("list".equals(screen)) {
                            showList();
                        }
                    }
                });
            }
        }, "froglog-mappings").start();
    }

    private void showList() {
        screen = "list";
        root.removeAllViews();
        root.addView(FroglogTheme.title(this, "Game mappings"));
        root.addView(muted("Each Cocoon game logs its sessions to the Froglog game shown here. A change applies to sessions that have not been sent yet. Ones already on Froglog stay where they are.", 14));
        status = muted(libraryLine(), 13);
        root.addView(gap(8));
        root.addView(status);
        if (!FroglogStore.signedIn(this)) {
            return;
        }
        root.addView(gap(12));
        root.addView(wide(FroglogTheme.button(this, "Map a Cocoon game", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPicker();
            }
        })));
        List<FroglogLinks.Mapping> all = FroglogStore.mappings(this, names);
        ArrayList<FroglogLinks.Mapping> mapped = new ArrayList<FroglogLinks.Mapping>();
        ArrayList<FroglogLinks.Mapping> declined = new ArrayList<FroglogLinks.Mapping>();
        for (int i = 0; i < all.size(); i++) {
            (all.get(i).declined ? declined : mapped).add(all.get(i));
        }
        root.addView(gap(16));
        root.addView(FroglogTheme.section(this, "Mapped"));
        if (mapped.isEmpty()) {
            root.addView(card(muted("Nothing is mapped yet. A game is mapped the first time one of its sessions is logged, matched by title or chosen under New games.", 14)));
        }
        for (int i = 0; i < mapped.size(); i++) {
            root.addView(mappingRow(mapped.get(i)));
        }
        if (!declined.isEmpty()) {
            root.addView(gap(16));
            root.addView(FroglogTheme.section(this, "Not logged"));
            for (int i = 0; i < declined.size(); i++) {
                root.addView(mappingRow(declined.get(i)));
            }
        }
        scroll.scrollTo(0, 0);
    }

    private View mappingRow(final FroglogLinks.Mapping mapping) {
        LinearLayout lines = column();
        lines.addView(FroglogTheme.text(this, mapping.title, 16, true));
        String platform = platformLabel(mapping.platform);
        String target;
        if (mapping.declined) {
            target = "Sessions are not sent to Froglog";
        } else {
            target = "Logs to " + froglogLabel(mapping);
        }
        lines.addView(muted(platform.isEmpty() ? target : platform + " · " + target, 13));
        View row = card(lines);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEdit(mapping.title, mapping.platform);
            }
        });
        return row;
    }

    private void showPicker() {
        screen = "pick";
        root.removeAllViews();
        root.addView(FroglogTheme.title(this, "Map a Cocoon game"));
        root.addView(muted("Games from your Cocoon library that do not have a mapping yet.", 14));
        root.addView(gap(12));
        final EditText filter = FroglogTheme.field(this, "Filter Cocoon games");
        root.addView(filter);
        rows = column();
        root.addView(rows);
        filter.addTextChangedListener(new Watcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                paintPicker(s == null ? "" : s.toString());
            }
        });
        paintPicker("");
        scroll.scrollTo(0, 0);
    }

    private void paintPicker(String query) {
        rows.removeAllViews();
        String wanted = FroglogMatch.normalize(query);
        int shown = 0;
        for (int i = 0; i < cocoon.size() && shown < ROWS; i++) {
            final CocoonLibrary.Game game = cocoon.get(i);
            final String platform = game.platformId == null ? "" : game.platformId;
            if (FroglogStore.link(this, FroglogMatch.linkKey(game.title, platform)) != null) {
                continue;
            }
            if (!wanted.isEmpty() && FroglogMatch.normalize(game.title).indexOf(wanted) < 0) {
                continue;
            }
            shown++;
            LinearLayout lines = column();
            lines.addView(FroglogTheme.text(this, game.title, 16, true));
            String label = platformLabel(platform);
            if (!label.isEmpty()) {
                lines.addView(muted(label, 13));
            }
            View row = card(lines);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEdit(game.title, platform);
                }
            });
            rows.addView(row);
        }
        if (shown == 0) {
            rows.addView(card(muted(wanted.isEmpty()
                    ? "Every Cocoon game already has a mapping."
                    : "No unmapped Cocoon game matches that.", 14)));
        }
    }

    private void showEdit(final String title, final String platform) {
        screen = "edit";
        editTitle = title;
        editPlatform = platform;
        final String key = FroglogMatch.linkKey(title, platform);
        FroglogLinks.Mapping current = current(key);
        root.removeAllViews();
        root.addView(FroglogTheme.section(this, "Cocoon game"));
        root.addView(FroglogTheme.title(this, title));
        String label = platformLabel(platform);
        if (!label.isEmpty()) {
            root.addView(muted(label, 14));
        }
        root.addView(gap(12));
        String now;
        if (current == null) {
            now = "Not mapped. The next session is matched by title, or waits under New games.";
        } else if (current.declined) {
            now = "Sessions for this game are not sent to Froglog.";
        } else {
            now = "Logs to " + froglogLabel(current) + ".";
        }
        status = muted(now, 14);
        root.addView(status);
        LinearLayout actions = column();
        if (current != null) {
            actions.addView(wide(FroglogTheme.secondary(this, current.declined ? "Log this game again" : "Remove mapping",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            FroglogStore.unlink(FroglogMappingsActivity.this, key);
                            FroglogSync.kick(FroglogMappingsActivity.this);
                            showList();
                        }
                    })));
        }
        if (current == null || !current.declined) {
            if (current != null) {
                actions.addView(gap(8));
            }
            actions.addView(wide(FroglogTheme.secondary(this, "Don't log this game", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FroglogStore.decline(FroglogMappingsActivity.this, title, platform);
                    FroglogStore.removePendingKey(FroglogMappingsActivity.this, title, platform);
                    showList();
                }
            })));
        }
        root.addView(card(actions));
        root.addView(gap(16));
        root.addView(FroglogTheme.section(this, current == null || current.declined
                ? "Map to a Froglog game" : "Change to another Froglog game"));
        if (!libraryLoaded || libraryError != null) {
            root.addView(card(muted(libraryLine(), 14)));
            scroll.scrollTo(0, 0);
            return;
        }
        final EditText filter = FroglogTheme.field(this, "Filter your library");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        filter.setLayoutParams(params);
        root.addView(filter);
        rows = column();
        root.addView(rows);
        filter.addTextChangedListener(new Watcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                paintLibrary(s == null ? "" : s.toString(), title, platform);
            }
        });
        paintLibrary("", title, platform);
        scroll.scrollTo(0, 0);
    }

    private void paintLibrary(String query, final String title, final String platform) {
        rows.removeAllViews();
        FroglogLinks.Mapping current = current(FroglogMatch.linkKey(title, platform));
        ArrayList<FroglogGame> ordered = new ArrayList<FroglogGame>();
        if (query.trim().isEmpty()) {
            FroglogGame suggested = FroglogMatch.best(library, title, platform);
            if (suggested != null) {
                ordered.add(suggested);
            }
        }
        for (int i = 0; i < library.size(); i++) {
            if (!ordered.contains(library.get(i))) {
                ordered.add(library.get(i));
            }
        }
        String wanted = FroglogMatch.normalize(query);
        String wantedTitle = FroglogMatch.normalizeTitle(query);
        int shown = 0;
        for (int i = 0; i < ordered.size() && shown < ROWS; i++) {
            final FroglogGame game = ordered.get(i);
            if (!wanted.isEmpty() && FroglogMatch.normalize(game.title).indexOf(wanted) < 0
                    && FroglogMatch.normalizeTitle(game.title).indexOf(wantedTitle) < 0) {
                continue;
            }
            shown++;
            boolean selected = current != null && !current.declined
                    && current.gameId == game.id && current.live == game.live;
            LinearLayout lines = column();
            lines.addView(FroglogTheme.text(this, game.title, 16, true));
            String meta = gameMeta(game);
            if (selected) {
                meta = meta.isEmpty() ? "Current mapping" : "Current mapping · " + meta;
            }
            if (!meta.isEmpty()) {
                lines.addView(muted(meta, 13));
            }
            View row = card(lines);
            if (!selected) {
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FroglogStore.link(FroglogMappingsActivity.this, title, platform, game.id, game.live);
                        FroglogSync.kick(FroglogMappingsActivity.this);
                        showList();
                    }
                });
            }
            rows.addView(row);
        }
        if (shown == 0) {
            rows.addView(card(muted(library.isEmpty()
                    ? "Your Froglog library is empty. Use Add a Cocoon game in the pod to create an entry."
                    : "Nothing in the library matches that.", 14)));
        }
    }

    private FroglogLinks.Mapping current(String key) {
        List<FroglogLinks.Mapping> all = FroglogStore.mappings(this, names);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).key.equals(key)) {
                return all.get(i);
            }
        }
        return null;
    }

    private String froglogLabel(FroglogLinks.Mapping mapping) {
        FroglogGame game = FroglogLinks.find(library, mapping);
        if (game != null) {
            return game.title + (game.live ? " (Live)" : "");
        }
        String id = (mapping.live ? "live game #" : "game #") + mapping.gameId;
        if (!libraryLoaded) {
            return "Froglog " + id;
        }
        return libraryError == null ? "Froglog " + id + ", which is no longer in your library" : "Froglog " + id;
    }

    private String libraryLine() {
        if (!FroglogStore.signedIn(this)) {
            return "Sign in to Froglog from the pod first.";
        }
        if (!libraryLoaded) {
            return "Loading your Froglog library…";
        }
        return libraryError == null ? "" : libraryError;
    }

    private String platformLabel(String platform) {
        if (platform == null || platform.isEmpty()) {
            return "";
        }
        String name = platformNames.get(platform);
        return name == null || name.isEmpty() ? platform : name;
    }

    private static String gameMeta(FroglogGame game) {
        StringBuilder out = new StringBuilder();
        if (game.platform != null && !game.platform.isEmpty()) {
            out.append(game.platform);
        }
        if (game.live) {
            out.append(out.length() == 0 ? "" : " · ").append("Live");
        }
        if (game.status != null && !game.status.isEmpty()) {
            out.append(out.length() == 0 ? "" : " · ").append(game.status);
        }
        return out.toString();
    }

    private View wide(View view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private LinearLayout card(View child) {
        return FroglogTheme.card(this, child);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView muted(String value, int sp) {
        TextView view = FroglogTheme.text(this, value, sp, false);
        view.setTextColor(FroglogTheme.MUTED);
        return view;
    }

    private View gap(int dp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp)));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private abstract static class Watcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}
    }
}
