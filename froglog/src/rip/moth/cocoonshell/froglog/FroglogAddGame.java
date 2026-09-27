package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Search Froglog, then create the Cocoon game if it is missing. */
public class FroglogAddGame extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PLATFORM = "platform";
    public static final String EXTRA_PLATFORM_LABEL = "platform_label";

    private EditText title;
    private EditText platform;
    private TextView status;
    private LinearLayout results;
    private String coverUrl;
    private boolean confirmNew;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = field(extra(EXTRA_TITLE));
        String shownPlatform = extra(EXTRA_PLATFORM_LABEL);
        platform = field(shownPlatform.isEmpty() ? extra(EXTRA_PLATFORM) : shownPlatform);
        status = text("Search Froglog, then add the game as public or private.", 14, false);
        status.setTextColor(Color.parseColor("#C8C2B8"));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        LinearLayout root = column();
        root.addView(text("Add to Froglog", 22, true));
        root.addView(gap());
        root.addView(title);
        root.addView(gap());
        root.addView(platform);
        root.addView(gap());
        root.addView(button("Search", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        }));
        root.addView(gap());
        root.addView(button("Add as public", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                create(true);
            }
        }));
        root.addView(gap());
        root.addView(button("Add as private", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                create(false);
            }
        }));
        root.addView(gap());
        root.addView(status);
        root.addView(gap());
        root.addView(results);
        setContentView(scroll(root));
        if (!title.getText().toString().trim().isEmpty()) {
            search();
        }
    }

    private void search() {
        if (!FroglogStore.signedIn(this)) {
            status.setText("Sign in to Froglog from the widget first.");
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FroglogClient.Recent library = FroglogClient.library(token);
                final FroglogGame existing = library.error == null
                        ? FroglogMatch.best(library.games, query, platformText) : null;
                String searchError = null;
                List<FroglogClient.Hit> hits = java.util.Collections.emptyList();
                if (existing == null) {
                    try {
                        hits = FroglogClient.search(token, query);
                    } catch (Exception e) {
                        searchError = e.getMessage();
                    }
                }
                final FroglogGame found = existing;
                final List<FroglogClient.Hit> foundHits = hits;
                final String error = searchError;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showSearch(found, foundHits, error);
                    }
                });
            }
        }, "froglog-search").start();
    }

    private void showSearch(FroglogGame existing, List<FroglogClient.Hit> hits, String error) {
        results.removeAllViews();
        if (existing != null) {
            status.setText("Already in your Froglog library as " + existing.title + ". The session can be linked without creating it again.");
            final FroglogGame game = existing;
            results.addView(button("Link " + existing.title, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    linkAndSend(game);
                }
            }));
            return;
        }
        if (error != null) {
            status.setText(error);
            return;
        }
        if (hits.isEmpty()) {
            status.setText("No catalog match. You can still add the title above.");
            return;
        }
        status.setText("Pick a match or add the title as typed.");
        for (int i = 0; i < hits.size(); i++) {
            final FroglogClient.Hit hit = hits.get(i);
            String label = hit.title + (hit.platform == null ? "" : " · " + hit.platform);
            results.addView(gap());
            results.addView(button(label, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    title.setText(hit.title);
                    if (hit.platform != null) {
                        platform.setText(hit.platform);
                    }
                    coverUrl = hit.coverUrl;
                    status.setText("Using " + hit.title + ". Choose public or private.");
                }
            }));
        }
    }

    private void create(final boolean isPublic) {
        if (!FroglogStore.signedIn(this)) {
            status.setText("Sign in to Froglog from the widget first.");
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
        final boolean separate = confirmNew;
        final String clientRef = "cocoon:" + FroglogMatch.linkKey(cocoonTitle(), extra(EXTRA_PLATFORM));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final FroglogCreate.Outcome outcome = FroglogClient.createGameKeyed(
                            token, name, platformName, cover, isPublic, clientRef, separate);
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
        results.addView(button("Log as a separate game", new View.OnClickListener() {
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

    private android.widget.ScrollView scroll(View child) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#121418"));
        scroll.addView(child);
        return scroll;
    }

    private LinearLayout column() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        return root;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor("#F4F1EA"));
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private EditText field(String value) {
        EditText view = new EditText(this);
        view.setText(value);
        view.setSingleLine(true);
        view.setTextColor(Color.parseColor("#F4F1EA"));
        view.setHintTextColor(Color.parseColor("#8A847C"));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#1C2028"));
        bg.setCornerRadius(dp(10));
        view.setBackground(bg);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextColor(Color.parseColor("#121418"));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#8BD17C"));
        bg.setCornerRadius(dp(12));
        view.setBackground(bg);
        view.setOnClickListener(listener);
        return view;
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
