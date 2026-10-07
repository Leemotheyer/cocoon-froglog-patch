package rip.moth.cocoonshell.froglog;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Cocoon pod for the Froglog account. Sign-in lives here, with the signed-in library under it.
 */
public class FroglogPodActivity extends FroglogActivity {
    /** Library filter to open on, one of the {@link FroglogGames} FILTER_ values. */
    public static final String EXTRA_FILTER = "froglog_filter";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int generation;
    private String filter = FroglogGames.FILTER_RECENT;
    private LinearLayout account;
    private LinearLayout stats;
    private LinearLayout follows;
    private LinearLayout pending;
    private View pendingCard;
    private LinearLayout games;
    private LinearLayout chips;
    private TextView status;
    private EditText username;
    private EditText password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent() == null ? null : getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            String wanted = extras.getString(EXTRA_FILTER);
            if (wanted != null) {
                filter = wanted;
            }
        }
        if (isWidgetConfigure() && FroglogStore.signedIn(this)) {
            finishPod();
            return;
        }
        setResult(RESULT_CANCELED);

        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        LinearLayout root = column(dp(20), dp(18));
        root.addView(header());
        account = column(0, dp(14));
        stats = column(0, dp(14));
        games = column(0, 0);
        root.addView(card(account));
        root.addView(gap(12));
        root.addView(card(stats));
        follows = column(0, 0);
        root.addView(card(follows));
        pending = column(0, 0);
        pendingCard = card(pending);
        root.addView(pendingCard);
        root.addView(gap(16));
        root.addView(section("Library"));
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, dp(8));
        root.addView(chips);
        paintChips();
        root.addView(games);
        scroll.addView(root);
        setContentView(scroll);
        paintSystemBars();
        bindAccount();
        load();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 31);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showPending();
        FroglogSync.kick(this);
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column(0, 0);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titles.addView(FroglogTheme.title(this, "Froglog"));
        TextView body = text("Your account, library, and sessions still waiting for a Froglog game.", 14, false);
        body.setTextColor(FroglogTheme.MUTED);
        titles.addView(body);
        row.addView(titles);
        TextView done = text("Done", 15, true);
        done.setTextColor(0xFFFFFFFF);
        done.setBackground(pill(FroglogTheme.ACCENT));
        done.setPadding(dp(16), dp(8), dp(16), dp(8));
        FroglogTheme.row(done, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishPod();
            }
        });
        row.addView(done);
        return row;
    }

    private View sessionVisibilityRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView label = text("Default session visibility", 14, false);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);
        final TextView value = text(sessionVisibilityLabel(FroglogStore.sessionsPublic(this)), 14, true);
        value.setTextColor(FroglogTheme.ACCENT);
        row.addView(value);
        FroglogTheme.row(row, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !FroglogStore.sessionsPublic(FroglogPodActivity.this);
                FroglogStore.setSessionsPublic(FroglogPodActivity.this, next);
                value.setText(sessionVisibilityLabel(next));
            }
        });
        return row;
    }

    private static String sessionVisibilityLabel(boolean sessionsPublic) {
        return sessionsPublic ? "Public" : "Private";
    }

    private void bindAccount() {
        account.removeAllViews();
        account.addView(section("Account"));
        if (!FroglogStore.signedIn(this)) {
            TextView copy = text("Sign in once. The password is sent to Froglog and is not stored on this device.", 13, false);
            copy.setTextColor(FroglogTheme.MUTED);
            account.addView(copy);
            account.addView(gap(10));
            username = field("Username");
            password = field("Password");
            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            account.addView(username);
            account.addView(gap(8));
            account.addView(password);
            account.addView(gap(10));
            account.addView(action("Sign in", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    signIn();
                }
            }));
            status = text("", 13, false);
            status.setTextColor(FroglogTheme.MUTED);
            account.addView(gap(8));
            account.addView(status);
            return;
        }
        TextView who = text(FroglogStore.username(this), 20, true);
        account.addView(who);
        TextView copy = text("Signed in. People you follow show in Cocoon's friends list. This library includes private games. New games holds sessions that still need a Froglog entry.", 13, false);
        copy.setTextColor(FroglogTheme.MUTED);
        account.addView(copy);
        account.addView(gap(10));
        account.addView(sessionVisibilityRow());
        account.addView(gap(10));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        Button add = action("Add a Cocoon game", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FroglogPodActivity.this, FroglogLibraryPicker.class));
            }
        });
        add.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button out = FroglogTheme.secondary(this, "Sign out", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FroglogStore.clear(FroglogPodActivity.this);
                FroglogSocial.clear();
                refreshWidgets();
                bindAccount();
                load();
            }
        });
        Button mappings = FroglogTheme.secondary(this, "Game mappings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FroglogPodActivity.this, FroglogMappingsActivity.class));
            }
        });
        LinearLayout.LayoutParams mappingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mappingsParams.topMargin = dp(8);
        mappings.setLayoutParams(mappingsParams);
        Button picnic = FroglogTheme.secondary(this, "Picnic screenshots", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FroglogPodActivity.this, FroglogPicnicActivity.class));
            }
        });
        LinearLayout.LayoutParams picnicParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        picnicParams.topMargin = dp(8);
        picnic.setLayoutParams(picnicParams);
        LinearLayout.LayoutParams outParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outParams.topMargin = dp(8);
        out.setLayoutParams(outParams);
        actions.addView(add);
        actions.addView(mappings);
        actions.addView(picnic);
        actions.addView(out);
        account.addView(actions);
    }

    private void paintChips() {
        chips.removeAllViews();
        String[] modes = {
                FroglogGames.FILTER_RECENT,
                FroglogGames.FILTER_PROGRESS,
                FroglogGames.FILTER_COMPLETED,
                FroglogGames.FILTER_LIVE
        };
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            boolean selected = mode.equals(filter);
            TextView chip = text(FroglogGames.filterLabel(mode), 12, selected);
            chip.setTextColor(selected ? 0xFFFFFFFF : FroglogTheme.INK);
            chip.setBackground(pill(selected ? FroglogTheme.ACCENT : FroglogTheme.FIELD));
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            chip.setLayoutParams(params);
            FroglogTheme.row(chip, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setFilter(mode);
                }
            });
            chips.addView(chip);
        }
    }

    private void signIn() {
        final String name = username.getText().toString().trim();
        final String pass = password.getText().toString();
        if (name.isEmpty() || pass.isEmpty()) {
            status.setText("Enter your Froglog username and password");
            return;
        }
        status.setText("Signing in…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final FroglogClient.Session session = FroglogClient.login(name, pass);
                    FroglogStore.save(FroglogPodActivity.this, session.token, session.username);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshWidgets();
                            if (isWidgetConfigure()) {
                                finishPod();
                                return;
                            }
                            bindAccount();
                            load();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText(e.getMessage() == null ? "Could not sign in" : e.getMessage());
                        }
                    });
                }
            }
        }, "froglog-pod-login").start();
    }

    private void load() {
        final int ticket = ++generation;
        stats.removeAllViews();
        games.removeAllViews();
        showFollows();
        if (!FroglogStore.signedIn(this)) {
            stats.addView(section("This month"));
            TextView signedOut = text("Sign in to see hours and your library.", 14, false);
            signedOut.setTextColor(FroglogTheme.MUTED);
            stats.addView(signedOut);
            return;
        }
        stats.addView(section("This month"));
        TextView waiting = text("Loading Froglog…", 14, false);
        waiting.setTextColor(FroglogTheme.MUTED);
        stats.addView(waiting);
        final String token = FroglogStore.token(this);
        final String mode = filter;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FroglogClient.Stats loadedStats = FroglogClient.stats(token, true);
                final FroglogClient.Recent loadedGames = FroglogClient.library(token, mode, 24);
                FroglogSocial.loadIntoCache(token, FroglogStore.username(FroglogPodActivity.this));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ticket != generation || isFinishing()) {
                            return;
                        }
                        showStats(loadedStats);
                        showFollows();
                        showGames(loadedGames);
                    }
                });
            }
        }, "froglog-pod").start();
    }

    private void showStats(FroglogClient.Stats loaded) {
        stats.removeAllViews();
        stats.addView(section("Play time"));
        if (loaded != null && loaded.error != null) {
            TextView error = text(loaded.error, 14, false);
            error.setTextColor(FroglogTheme.MUTED);
            stats.addView(error);
            return;
        }
        if (loaded == null || loaded.summary == null) {
            return;
        }
        final FroglogStatsSummary summary = loaded.summary;
        LinearLayout first = tiles();
        first.addView(tile(hours(summary.weekHours), "Last 7 days", FroglogGames.FILTER_RECENT));
        first.addView(tile(hours(summary.monthHours), "This month", FroglogGames.FILTER_RECENT));
        stats.addView(first);
        LinearLayout second = tiles();
        second.addView(tile(hours(summary.yearHours), "This year", FroglogGames.FILTER_PROGRESS));
        second.addView(tile(summary.streak + (summary.streak == 1 ? " day" : " days"), "Streak", null));
        stats.addView(second);
        StringBuilder done = new StringBuilder();
        done.append(summary.yearCompleted).append(summary.yearCompleted == 1 ? " game" : " games")
                .append(" completed this year");
        if (summary.completionRate >= 0) {
            done.append(" · ").append(summary.completionRate).append("% completion");
        }
        TextView rate = text(done.toString(), 13, false);
        rate.setTextColor(FroglogTheme.MUTED);
        rate.setPadding(0, dp(10), 0, 0);
        FroglogTheme.row(rate, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFilter(FroglogGames.FILTER_COMPLETED);
            }
        });
        stats.addView(rate);
        if (summary.topTitle != null && !summary.topTitle.isEmpty()) {
            final FroglogGame top = new FroglogGame(summary.topId, summary.topLive, summary.topTitle, null,
                    summary.topCover, null, null, null, 0, hours(summary.topHours) + " this month", 0L);
            stats.addView(gap(6));
            stats.addView(FroglogCards.gameRow(this, "Top this month · " + top.title, top.meta, top.coverUrl,
                    summary.topId > 0 ? new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(FroglogGameDetail.intent(FroglogPodActivity.this, top, true));
                        }
                    } : null));
        }
    }

    private static String hours(double value) {
        String label = FroglogGames.hoursLabel(Double.valueOf(value));
        return label == null ? "0h" : label;
    }

    private LinearLayout tiles() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    /** One figure with its label. A tile with a filter opens the library on that filter. */
    private View tile(String value, String label, final String target) {
        LinearLayout box = column(dp(14), dp(10));
        box.setBackground(rounded(FroglogTheme.FIELD, dp(14)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = dp(4);
        params.leftMargin = dp(4);
        box.setLayoutParams(params);
        TextView figure = text(value, 22, true);
        box.addView(figure);
        TextView caption = text(label, 12, false);
        caption.setTextColor(FroglogTheme.MUTED);
        box.addView(caption);
        if (target != null) {
            FroglogTheme.row(box, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setFilter(target);
                }
            });
        }
        return box;
    }

    private void setFilter(String mode) {
        if (mode.equals(filter)) {
            return;
        }
        filter = mode;
        paintChips();
        load();
    }

    private void showPending() {
        if (pending == null || pendingCard == null) {
            return;
        }
        pending.removeAllViews();
        if (!FroglogStore.signedIn(this)) {
            pendingCard.setVisibility(View.GONE);
            return;
        }
        pendingCard.setVisibility(View.VISIBLE);
        List<FroglogQueue.Item> items = FroglogStore.pending(this);
        java.util.ArrayList<FroglogQueue.Item> waiting = new java.util.ArrayList<FroglogQueue.Item>();
        java.util.ArrayList<FroglogQueue.Item> unmapped = new java.util.ArrayList<FroglogQueue.Item>();
        for (int i = 0; i < items.size(); i++) {
            FroglogQueue.Item item = items.get(i);
            String link = FroglogStore.link(this, FroglogMatch.linkKey(item.title, item.platform));
            if (link != null && link.indexOf(':') > 0) {
                waiting.add(item);
            } else {
                unmapped.add(item);
            }
        }
        if (!waiting.isEmpty()) {
            pending.addView(section("Waiting to upload"));
            TextView note = text("These sessions are saved on this device and upload when Froglog can be reached.", 13, false);
            note.setTextColor(FroglogTheme.MUTED);
            pending.addView(note);
            for (int i = 0; i < waiting.size(); i++) {
                pending.addView(pendingRow(waiting.get(i)));
            }
        }
        pending.addView(section("New games"));
        if (unmapped.isEmpty()) {
            TextView empty = text("Sessions Cocoon could not match wait here. Map one to a Froglog game, create an entry, or dismiss it.", 14, false);
            empty.setTextColor(FroglogTheme.MUTED);
            pending.addView(empty);
            return;
        }
        for (int i = 0; i < unmapped.size(); i++) {
            pending.addView(pendingRow(unmapped.get(i)));
        }
    }

    private View pendingRow(final FroglogQueue.Item item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(text(item.title.isEmpty() ? "Unknown session" : item.title, 16, true));
        String meta = item.minutes + "m"
                + (item.date.isEmpty() ? "" : " · " + item.date)
                + (item.platform.isEmpty() ? "" : " · " + item.platform);
        TextView line = text(meta, 13, false);
        line.setTextColor(FroglogTheme.MUTED);
        row.addView(line);
        if (item.error != null && !item.error.isEmpty()) {
            TextView error = text(item.error, 13, false);
            error.setTextColor(0xFFE7B3A2);
            row.addView(error);
        }
        FroglogTheme.row(row, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent open = new Intent(FroglogPodActivity.this, FroglogMapActivity.class);
                open.putExtra(FroglogMapActivity.EXTRA_TITLE, item.title);
                open.putExtra(FroglogMapActivity.EXTRA_PLATFORM, item.platform);
                open.putExtra(FroglogMapActivity.EXTRA_MINUTES, item.minutes);
                open.putExtra(FroglogMapActivity.EXTRA_DATE, item.date);
                open.putExtra(FroglogMapActivity.EXTRA_SYNC, item.sync);
                startActivity(open);
            }
        });
        return row;
    }

    private void showFollows() {
        follows.removeAllViews();
        follows.addView(section("Following"));
        if (!FroglogStore.signedIn(this)) {
            TextView signedOut = text("Sign in to see people you follow.", 14, false);
            signedOut.setTextColor(FroglogTheme.MUTED);
            follows.addView(signedOut);
            return;
        }
        java.util.List<FroglogFollow> people = FroglogSocial.snapshot();
        if (people.isEmpty()) {
            TextView empty = text("No recent activity from people you follow.", 14, false);
            empty.setTextColor(FroglogTheme.MUTED);
            follows.addView(empty);
            return;
        }
        for (int i = 0; i < people.size(); i++) {
            follows.addView(followRow(people.get(i)));
        }
    }

    private View followRow(final FroglogFollow person) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(text(person.name, 16, true));
        TextView status = text(person.status, 13, false);
        status.setTextColor(FroglogTheme.MUTED);
        row.addView(status);
        FroglogTheme.row(row, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent open = new Intent(FroglogPodActivity.this, FroglogFriendActivity.class);
                open.putExtra(FroglogFriendActivity.EXTRA_USERNAME, person.username);
                open.putExtra(FroglogFriendActivity.EXTRA_NAME, person.name);
                open.putExtra(FroglogFriendActivity.EXTRA_STATUS, person.status);
                startActivity(open);
            }
        });
        return row;
    }

    private void showGames(FroglogClient.Recent recent) {
        games.removeAllViews();
        if (recent != null && recent.error != null) {
            TextView error = text(recent.error, 14, false);
            error.setTextColor(FroglogTheme.MUTED);
            games.addView(error);
            return;
        }
        if (recent == null || recent.games.isEmpty()) {
            TextView empty = text("No games in " + FroglogGames.filterLabel(filter) + ".", 14, false);
            empty.setTextColor(FroglogTheme.MUTED);
            games.addView(empty);
            return;
        }
        for (int i = 0; i < recent.games.size(); i++) {
            games.addView(gameRow(recent.games.get(i)));
        }
    }

    private View gameRow(final FroglogGame game) {
        View row = FroglogCards.gameRow(this, game.title, game.meta, game.coverUrl, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(FroglogGameDetail.intent(FroglogPodActivity.this, game, true));
            }
        });
        String visibility = FroglogStore.visibility(this, FroglogLinks.value(game.id, game.live));
        if (!FroglogStore.VISIBILITY_DEFAULT.equals(visibility)) {
            LinearLayout inner = (LinearLayout) ((LinearLayout) row).getChildAt(0);
            LinearLayout text = (LinearLayout) inner.getChildAt(1);
            TextView chip = FroglogTheme.chip(this, FroglogStore.VISIBILITY_PUBLIC.equals(visibility)
                    ? "Always public" : "Always private", false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(4);
            chip.setLayoutParams(params);
            text.addView(chip);
        }
        return row;
    }

    private boolean isWidgetConfigure() {
        Intent intent = getIntent();
        return intent != null
                && AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(intent.getAction());
    }

    private void finishPod() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
        }
        finish();
    }

    private void refreshWidgets() {
        FroglogWidgetTheme.refresh(this);
    }

    private void paintSystemBars() {
        FroglogTheme.paintSystemBars(this);
    }

    private LinearLayout card(View child) {
        return FroglogTheme.card(this, child);
    }

    private LinearLayout column(int padH, int padV) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padH, padV, padH, padV);
        return layout;
    }

    private TextView section(String value) {
        return FroglogTheme.section(this, value);
    }

    private TextView text(String value, int sp, boolean bold) {
        return FroglogTheme.text(this, value, sp, bold);
    }

    private EditText field(String hint) {
        return FroglogTheme.field(this, hint);
    }

    private Button action(String label, View.OnClickListener listener) {
        return FroglogTheme.button(this, label, listener);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable pill(int color) {
        return rounded(color, dp(20));
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
