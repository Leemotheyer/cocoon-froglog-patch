package rip.moth.cocoonshell.froglog;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Search Froglog's catalog, look at a result's card, then create the Cocoon game (and send its
 * waiting sessions) or, in Up Next mode, add it to the Froglog wishlist.
 */
public class FroglogAddGame extends FroglogActivity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PLATFORM = "platform";
    public static final String EXTRA_PLATFORM_LABEL = "platform_label";
    /** {@link #MODE_UP_NEXT} adds to the Froglog wishlist instead of the library. */
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_UP_NEXT = "up_next";

    private EditText title;
    private EditText platform;
    private TextView status;
    private LinearLayout results;
    private String coverUrl;
    private FroglogClient.Hit chosen;
    private boolean confirmNew;
    private boolean upNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        upNext = MODE_UP_NEXT.equals(extra(EXTRA_MODE));
        title = FroglogTheme.field(this, "Game title");
        title.setText(extra(EXTRA_TITLE));
        title.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        title.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                search();
                return true;
            }
        });
        String shownPlatform = extra(EXTRA_PLATFORM_LABEL);
        platform = FroglogTheme.field(this, "Platform");
        platform.setText(shownPlatform.isEmpty() ? extra(EXTRA_PLATFORM) : shownPlatform);
        status = FroglogTheme.muted(this, upNext
                ? "Search Froglog, then pick the game to add to Up Next."
                : "Search Froglog, then pick a result to see its details.", 14);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(title);
        if (!upNext) {
            form.addView(gap());
            form.addView(platform);
        }
        form.addView(gap());
        form.addView(button("Search", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        }));
        form.addView(gap());
        if (upNext) {
            form.addView(FroglogTheme.secondary(this, "Add the title as typed", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chosen = null;
                    addToUpNext(null);
                }
            }));
        } else {
            LinearLayout pair = new LinearLayout(this);
            pair.setOrientation(LinearLayout.HORIZONTAL);
            Button pub = FroglogTheme.secondary(this, "Add as typed, public", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chosen = null;
                    coverUrl = null;
                    create(true);
                }
            });
            Button priv = FroglogTheme.secondary(this, "Private", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chosen = null;
                    coverUrl = null;
                    create(false);
                }
            });
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            right.leftMargin = dp(8);
            pair.addView(pub, left);
            pair.addView(priv, right);
            form.addView(pair);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.addView(FroglogTheme.section(this, upNext ? "Froglog Up Next" : "Add to Froglog"));
        String cocoon = extra(EXTRA_TITLE);
        root.addView(FroglogTheme.title(this, cocoon.isEmpty() ? (upNext ? "Add to Up Next" : "Add a game") : cocoon));
        root.addView(FroglogTheme.card(this, form));
        root.addView(gap());
        root.addView(status);
        root.addView(results);
        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        scroll.addView(root);
        setContentView(scroll);
        FroglogTheme.paintSystemBars(this);
        if (!title.getText().toString().trim().isEmpty()) {
            search();
        }
    }

    private void search() {
        if (!FroglogStore.signedIn(this)) {
            status.setText("Sign in to Froglog from the pod first.");
            return;
        }
        final String query = title.getText().toString().trim();
        if (query.isEmpty()) {
            status.setText("Enter the game title.");
            return;
        }
        status.setText("Searching…");
        results.removeAllViews();
        final String token = FroglogStore.token(this);
        final String platformText = platform.getText().toString().trim();
        final boolean wishlist = upNext;
        new Thread(new Runnable() {
            @Override
            public void run() {
                FroglogGame existing = null;
                if (!wishlist) {
                    FroglogClient.Recent library = FroglogClient.library(token);
                    existing = library.error == null ? FroglogMatch.best(library.games, query, platformText) : null;
                }
                String searchError = null;
                List<FroglogClient.Hit> hits = java.util.Collections.emptyList();
                try {
                    hits = FroglogClient.search(token, query);
                } catch (Exception e) {
                    searchError = e.getMessage();
                }
                final FroglogGame found = existing;
                final List<FroglogClient.Hit> foundHits = hits;
                final String error = searchError;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            showSearch(found, foundHits, error);
                        }
                    }
                });
            }
        }, "froglog-search").start();
    }

    private void showSearch(final FroglogGame existing, List<FroglogClient.Hit> hits, String error) {
        results.removeAllViews();
        if (existing != null) {
            results.addView(FroglogTheme.section(this, "Already in your library"));
            results.addView(FroglogCards.gameRow(this, existing.title, existing.meta, existing.coverUrl,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showExisting(existing);
                        }
                    }));
            results.addView(gap());
        }
        if (error != null) {
            status.setText(error);
            return;
        }
        if (hits.isEmpty()) {
            status.setText(existing != null
                    ? "Log to the library entry above, or add the title as typed."
                    : "No catalog match. You can still add the title as typed.");
            return;
        }
        status.setText(existing != null
                ? "Use the library entry, or pick a catalog result to add a new one."
                : "Pick a result to see its details.");
        results.addView(FroglogTheme.section(this, "Froglog catalog"));
        for (int i = 0; i < hits.size(); i++) {
            final FroglogClient.Hit hit = hits.get(i);
            StringBuilder meta = new StringBuilder();
            if (hit.released != null && hit.released.length() >= 4) {
                meta.append(hit.released.substring(0, 4));
            }
            if (hit.developers != null) {
                meta.append(meta.length() == 0 ? "" : " · ").append(hit.developers);
            }
            if (hit.platform != null) {
                meta.append(meta.length() == 0 ? "" : " · ").append(hit.platform);
            }
            results.addView(FroglogCards.gameRow(this, hit.title, meta.toString(), hit.coverUrl,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showHit(hit);
                        }
                    }));
        }
    }

    private void showExisting(final FroglogGame game) {
        FroglogCards.Detail detail = FroglogCards.Detail.of(game);
        detail.note = "Sessions for " + cocoonTitle() + " will log to this entry.";
        FroglogCards.confirm(this, detail, "Log to this game", new Runnable() {
            @Override
            public void run() {
                linkAndSend(game);
            }
        });
    }

    private void showHit(final FroglogClient.Hit hit) {
        FroglogCards.Detail detail = FroglogCards.Detail.of(hit);
        if (upNext) {
            FroglogCards.confirm(this, detail, new FroglogCards.Action("Add to Up Next", new Runnable() {
                @Override
                public void run() {
                    addToUpNext(hit);
                }
            }));
            return;
        }
        detail.note = "Adds " + hit.title + " to your Froglog library and logs " + cocoonTitle() + " there.";
        FroglogCards.confirm(this, detail,
                new FroglogCards.Action("Add to Froglog", new Runnable() {
                    @Override
                    public void run() {
                        pick(hit);
                        create(true);
                    }
                }),
                new FroglogCards.Action("Add as private", new Runnable() {
                    @Override
                    public void run() {
                        pick(hit);
                        create(false);
                    }
                }),
                new FroglogCards.Action("Add to Up Next instead", new Runnable() {
                    @Override
                    public void run() {
                        addToUpNext(hit);
                    }
                }));
    }

    private void pick(FroglogClient.Hit hit) {
        chosen = hit;
        title.setText(hit.title);
        if (hit.platform != null && platform.getText().toString().trim().isEmpty()) {
            platform.setText(hit.platform);
        }
        coverUrl = hit.coverUrl;
    }

    private void addToUpNext(final FroglogClient.Hit hit) {
        if (!FroglogStore.signedIn(this)) {
            status.setText("Sign in to Froglog from the pod first.");
            return;
        }
        final String name = hit != null ? hit.title : title.getText().toString().trim();
        if (name.isEmpty()) {
            status.setText("Enter the game title.");
            return;
        }
        status.setText("Adding " + name + " to Up Next…");
        final String token = FroglogStore.token(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    FroglogClient.addToWishlist(token, name, hit);
                } catch (Exception e) {
                    error = e.getMessage() == null ? "Could not add it to Up Next" : e.getMessage();
                }
                final String failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        if (failure == null) {
                            android.widget.Toast.makeText(FroglogAddGame.this, name + " is in Up Next",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            status.setText(failure);
                        }
                    }
                });
            }
        }, "froglog-up-next").start();
    }

    private void create(final boolean isPublic) {
        if (!FroglogStore.signedIn(this)) {
            status.setText("Sign in to Froglog from the pod first.");
            return;
        }
        final String name = title.getText().toString().trim();
        final String platformName = platform.getText().toString().trim();
        if (name.isEmpty()) {
            status.setText("Enter the game title.");
            return;
        }
        status.setText(confirmNew ? "Logging it as a separate game…" : "Adding…");
        final String token = FroglogStore.token(this);
        final String cover = coverUrl;
        final FroglogClient.Hit catalog = chosen;
        final boolean separate = confirmNew;
        final String clientRef = "cocoon:" + FroglogMatch.linkKey(cocoonTitle(), extra(EXTRA_PLATFORM));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final FroglogCreate.Outcome outcome = FroglogClient.createGameKeyed(
                            token, name, platformName, cover, isPublic, clientRef, separate, catalog);
                    if (outcome.needsChoice()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showConflict(outcome.existingId, outcome.existingTitle, name, platformName, isPublic);
                            }
                        });
                        return;
                    }
                    if (outcome.error != null || outcome.createdId < 0) {
                        throw new IllegalStateException(outcome.error == null ? "Froglog did not return a game id" : outcome.error);
                    }
                    final FroglogGame created = new FroglogGame(outcome.createdId, false, name, platformName, cover, "In Progress", "", null, 0, "", 0);
                    try {
                        FroglogClient.ensureTracking(token, created.id, new java.text.SimpleDateFormat(
                                "yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()),
                                FroglogStore.sessionsPublic(FroglogAddGame.this, created));
                    } catch (Exception ignored) {
                        // Session close still stamps the date. The row exists either way.
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            linkAndSend(created);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText(e.getMessage() == null ? "Could not add the game" : e.getMessage());
                        }
                    });
                }
            }
        }, "froglog-create").start();
    }

    private void showConflict(final long existingId, String existingTitle, final String name, final String platformName, final boolean isPublic) {
        final String label = existingTitle == null || existingTitle.isEmpty() ? name : existingTitle;
        status.setText("Froglog already has " + label + ". Add this time to that entry, or log it as a separate game.");
        results.removeAllViews();
        results.addView(button("Add time to " + label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                linkAndSend(new FroglogGame(existingId, false, label, platformName, coverUrl, "", "", null, 0, "", 0));
            }
        }));
        results.addView(gap());
        results.addView(FroglogTheme.secondary(this, "Log as a separate game", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmNew = true;
                create(isPublic);
            }
        }));
    }

    private void linkAndSend(final FroglogGame game) {
        if (!FroglogStore.signedIn(this)) {
            status.setText("Sign in to Froglog from the pod first.");
            return;
        }
        status.setText("Logging…");
        final String token = FroglogStore.token(this);
        final String cocoonTitle = cocoonTitle();
        final String cocoonPlatform = extra(EXTRA_PLATFORM);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String error = FroglogSubmit.send(FroglogAddGame.this, token, game,
                        cocoonTitle.isEmpty() ? game.title : cocoonTitle, cocoonPlatform, FroglogSubmit.NOTES_MAPPED);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            android.widget.Toast.makeText(FroglogAddGame.this, cocoonTitle() + " logs to " + game.title,
                                    android.widget.Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            status.setText(error + " It stays in New games so you can retry.");
                        }
                    }
                });
            }
        }, "froglog-create-log").start();
    }

    private String cocoonTitle() {
        String cocoonTitle = extra(EXTRA_TITLE);
        if (cocoonTitle.isEmpty()) {
            cocoonTitle = title.getText().toString().trim();
        }
        return cocoonTitle;
    }

    private String extra(String key) {
        String value = getIntent() == null ? null : getIntent().getStringExtra(key);
        return value == null ? "" : value;
    }

    private Button button(String label, View.OnClickListener listener) {
        return FroglogTheme.button(this, label, listener);
    }

    private View gap() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
