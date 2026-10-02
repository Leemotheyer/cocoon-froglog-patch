package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps one unknown Cocoon session onto a Froglog game: an existing library entry, a new entry,
 * or a dismissal. The same screen retries a post that failed.
 */
public class FroglogMapActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PLATFORM = "platform";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_SYNC = "sync";

    private static final int INK = FroglogTheme.INK;
    private static final int CARD = FroglogTheme.FIELD;
    private static final int CREAM = FroglogTheme.INK;
    private static final int MUTED = FroglogTheme.MUTED;
    private static final int GREEN = FroglogTheme.ACCENT;

    private TextView status;
    private LinearLayout actions;
    private LinearLayout libraryList;
    private LinearLayout libraryRows;
    private EditText filter;
    private List<FroglogGame> library = new ArrayList<FroglogGame>();
    private boolean libraryOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String title = extra(EXTRA_TITLE);
        final String platform = extra(EXTRA_PLATFORM);
        final int minutes = getIntent().getIntExtra(EXTRA_MINUTES, 0);
        final String date = extra(EXTRA_DATE);
        final String sync = extra(EXTRA_SYNC);
        if (!sync.isEmpty() && !FroglogStore.posted(this, sync)) {
            FroglogStore.enqueuePending(this, title, platform, minutes, date, sync);
        }

        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.addView(FroglogTheme.section(this, "New game"));
        root.addView(FroglogTheme.title(this, title.isEmpty() ? "Unknown session" : title));
        TextView meta = text(minutes + "m"
                + (date.isEmpty() ? "" : " · " + date)
                + (platform.isEmpty() ? "" : " · " + platform), 14, false);
        meta.setTextColor(MUTED);
        root.addView(meta);
        root.addView(gap(12));
        status = text("Looking through your Froglog library…", 14, false);
        status.setTextColor(MUTED);
        root.addView(status);
        root.addView(gap(14));
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        root.addView(FroglogTheme.card(this, actions));
        libraryList = new LinearLayout(this);
        libraryList.setOrientation(LinearLayout.VERTICAL);
        root.addView(gap(8));
        root.addView(libraryList);
        scroll.addView(root);
        setContentView(scroll);
        paintSystemBars();
        paintActions(title, platform, minutes, sync);
        loadLibrary(title, platform);
    }

    private void paintActions(final String title, final String platform, int minutes, final String sync) {
        actions.removeAllViews();
        if (!FroglogStore.signedIn(this)) {
            status.setText("Sign in to Froglog from the pod first.");
            return;
        }
        String queuedError = queuedError(sync);
        if (!queuedError.isEmpty()) {
            status.setText(queuedError);
        }
        final FroglogGame linked = linkedGame(title, platform);
        if (linked != null) {
            actions.addView(action("Send to the mapped game", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    send(linked, title, platform);
                }
            }));
            actions.addView(gap(8));
        }
        actions.addView(action("Map to an existing game", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                libraryOpen = true;
                showLibrary("");
            }
        }));
        actions.addView(gap(8));
        actions.addView(action("Create a new Froglog game", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FroglogMapActivity.this, FroglogAddGame.class);
                intent.putExtra(FroglogAddGame.EXTRA_TITLE, title);
                intent.putExtra(FroglogAddGame.EXTRA_PLATFORM, platform);
                intent.putExtra(EXTRA_DATE, extra(EXTRA_DATE));
                intent.putExtra(EXTRA_MINUTES, minutes);
                intent.putExtra(EXTRA_SYNC, sync);
                startActivity(intent);
                finish();
            }
        }));
        actions.addView(gap(8));
        actions.addView(action("Dismiss this session", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FroglogStore.removePending(FroglogMapActivity.this, sync);
                finish();
            }
        }));
        actions.addView(gap(8));
        actions.addView(action("Don't ask for this game", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FroglogStore.decline(FroglogMapActivity.this, title, platform);
                FroglogStore.removePendingKey(FroglogMapActivity.this, title, platform);
                finish();
            }
        }));
    }

    private void loadLibrary(final String title, final String platform) {
        if (!FroglogStore.signedIn(this)) {
            return;
        }
        final String token = FroglogStore.token(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FroglogClient.Recent loaded = FroglogClient.library(token);
                final FroglogGame found = loaded.error == null
                        ? FroglogMatch.best(loaded.games, title, platform) : null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        library = loaded.games == null ? new ArrayList<FroglogGame>() : loaded.games;
                        if (loaded.error != null) {
                            status.setText(loaded.error);
                            return;
                        }
                        if (found != null) {
                            offerMatch(found, title, platform);
                        } else if (queuedError(extra(EXTRA_SYNC)).isEmpty()) {
                            status.setText("No close match. Map it to a library game, or create a new one.");
                        }
                        if (libraryOpen) {
                            showLibrary(filter == null ? "" : filter.getText().toString());
                        }
                    }
                });
            }
        }, "froglog-map").start();
    }

    private void offerMatch(final FroglogGame found, final String title, final String platform) {
        String line = "Closest match is " + found.title
                + (found.platform == null || found.platform.isEmpty() ? "" : " · " + found.platform)
                + (found.status == null || found.status.isEmpty() ? "" : " · " + found.status);
        if ("Completed".equals(found.status) || "DNF".equals(found.status)) {
            line = line + ". Logging will mark it in progress again.";
        }
        if (queuedError(extra(EXTRA_SYNC)).isEmpty()) {
            status.setText(line);
        }
        TextView use = action("Log to " + found.title, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(found, title, platform);
            }
        });
        actions.addView(use, 0);
        actions.addView(gap(8), 1);
    }

    private void showLibrary(String query) {
        if (filter == null) {
            filter = FroglogTheme.field(this, "Filter your library");
            filter.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    showLibrary(s == null ? "" : s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
            libraryList.addView(filter);
            libraryRows = new LinearLayout(this);
            libraryRows.setOrientation(LinearLayout.VERTICAL);
            libraryList.addView(libraryRows);
        }
        libraryRows.removeAllViews();
        String wanted = FroglogMatch.normalize(query);
        int shown = 0;
        for (int i = 0; i < library.size() && shown < 40; i++) {
            final FroglogGame game = library.get(i);
            if (!wanted.isEmpty() && FroglogMatch.normalize(game.title).indexOf(wanted) < 0
                    && FroglogMatch.normalizeTitle(game.title).indexOf(FroglogMatch.normalizeTitle(query)) < 0) {
                continue;
            }
            shown++;
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.addView(text(game.title, 16, true));
            String meta = (game.platform == null || game.platform.isEmpty() ? "" : game.platform)
                    + (game.live ? (game.platform == null || game.platform.isEmpty() ? "" : " · ") + "Live" : "")
                    + (game.status == null || game.status.isEmpty() ? "" : ((game.platform == null || game.platform.isEmpty()) && !game.live ? "" : " · ") + game.status);
            if (!meta.isEmpty()) {
                TextView line = text(meta, 13, false);
                line.setTextColor(MUTED);
                wrap.addView(line);
            }
            View card = FroglogTheme.card(this, wrap);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    send(game, extra(EXTRA_TITLE), extra(EXTRA_PLATFORM));
                }
            });
            libraryRows.addView(card);
        }
        if (shown == 0) {
            TextView empty = text(library.isEmpty() ? "Your Froglog library is empty." : "Nothing in the library matches that.", 14, false);
            empty.setTextColor(MUTED);
            libraryRows.addView(empty);
        }
    }

    private void send(final FroglogGame game, final String title, final String platform) {
        status.setText("Logging…");
        final String token = FroglogStore.token(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String error = FroglogSubmit.send(FroglogMapActivity.this, token, game, title, platform,
                        FroglogSubmit.NOTES_MAPPED);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        if (error == null) {
                            finish();
                        } else {
                            status.setText(error + " It stays in New games so you can retry.");
                        }
                    }
                });
            }
        }, "froglog-map-send").start();
    }

    private FroglogGame linkedGame(String title, String platform) {
        String link = FroglogStore.link(this, FroglogMatch.linkKey(title, platform));
        if (link == null || "no".equals(link) || link.indexOf(':') < 0) {
            return null;
        }
        boolean live = link.startsWith("live:");
        if (!live && !link.startsWith("game:")) {
            return null;
        }
        try {
            long id = Long.parseLong(link.substring(link.indexOf(':') + 1));
            return new FroglogGame(id, live, title, platform, null, "", "", null, 0, "", 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String queuedError(String sync) {
        List<FroglogQueue.Item> items = FroglogStore.pending(this);
        for (int i = 0; i < items.size(); i++) {
            FroglogQueue.Item item = items.get(i);
            if (sync.equals(item.sync) && item.error != null && !item.error.isEmpty()) {
                return item.error;
            }
        }
        return "";
    }

    private String extra(String key) {
        String value = getIntent() == null ? null : getIntent().getStringExtra(key);
        return value == null ? "" : value;
    }

    private void paintSystemBars() {
        FroglogTheme.paintSystemBars(this);
    }

    private TextView action(String label, View.OnClickListener listener) {
        Button view = FroglogTheme.button(this, label, listener);
        return view;
    }

    private TextView text(String value, int sp, boolean bold) {
        return FroglogTheme.text(this, value, sp, bold);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private View gap(int dp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp)));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
