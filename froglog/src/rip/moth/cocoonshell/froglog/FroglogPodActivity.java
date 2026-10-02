package rip.moth.cocoonshell.froglog;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Cocoon pod for the Froglog account. Sign-in lives here, with the signed-in library under it.
 */
public class FroglogPodActivity extends Activity {
    private static final int INK = FroglogTheme.INK;
    private static final int CARD = FroglogTheme.FIELD;
    private static final int CREAM = FroglogTheme.INK;
    private static final int MUTED = FroglogTheme.MUTED;
    private static final int GREEN = FroglogTheme.ACCENT;

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
        body.setTextColor(MUTED);
        titles.addView(body);
        row.addView(titles);
        TextView done = text("Done", 15, true);
        done.setTextColor(0xFFFFFFFF);
        done.setBackground(pill(GREEN));
        done.setPadding(dp(16), dp(8), dp(16), dp(8));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishPod();
            }
        });
        row.addView(done);
        return row;
    }

    private void bindAccount() {
        account.removeAllViews();
        account.addView(section("Account"));
        if (!FroglogStore.signedIn(this)) {
            TextView copy = text("Sign in once. The password is sent to Froglog and is not stored on this device.", 13, false);
            copy.setTextColor(MUTED);
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
            status.setTextColor(MUTED);
            account.addView(gap(8));
            account.addView(status);
            return;
        }
        TextView who = text(FroglogStore.username(this), 20, true);
        account.addView(who);
        TextView copy = text("Signed in. People you follow show in Cocoon's friends list. This library includes private games. New games holds sessions that still need a Froglog entry.", 13, false);
        copy.setTextColor(MUTED);
        account.addView(copy);
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
        LinearLayout.LayoutParams outParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outParams.topMargin = dp(8);
        out.setLayoutParams(outParams);
        actions.addView(add);
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
            chip.setTextColor(selected ? 0xFFFFFFFF : INK);
            chip.setBackground(pill(selected ? GREEN : FroglogTheme.FIELD));
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            chip.setLayoutParams(params);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filter = mode;
                    paintChips();
                    load();
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
            signedOut.setTextColor(MUTED);
            stats.addView(signedOut);
            return;
        }
        stats.addView(section("This month"));
        TextView waiting = text("Loading Froglog…", 14, false);
        waiting.setTextColor(MUTED);
        stats.addView(waiting);
        final String token = FroglogStore.token(this);
        final String mode = filter;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FroglogClient.Stats loadedStats = FroglogClient.stats(token);
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
        stats.addView(section("Hours"));
        if (loaded != null && loaded.error != null) {
            TextView error = text(loaded.error, 14, false);
            error.setTextColor(MUTED);
            stats.addView(error);
            return;
        }
        if (loaded == null) {
            return;
        }
        stats.addView(text(loaded.monthLine, 16, true));
        stats.addView(text(loaded.yearLine, 16, true));
        TextView rate = text(loaded.rateLine, 14, false);
        rate.setTextColor(MUTED);
        stats.addView(rate);
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
            note.setTextColor(MUTED);
            pending.addView(note);
            for (int i = 0; i < waiting.size(); i++) {
                pending.addView(pendingRow(waiting.get(i)));
            }
        }
        pending.addView(section("New games"));
        if (unmapped.isEmpty()) {
            TextView empty = text("Sessions Cocoon could not match wait here. Map one to a Froglog game, create an entry, or dismiss it.", 14, false);
            empty.setTextColor(MUTED);
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
        line.setTextColor(MUTED);
        row.addView(line);
        if (item.error != null && !item.error.isEmpty()) {
            TextView error = text(item.error, 13, false);
            error.setTextColor(0xFFE7B3A2);
            row.addView(error);
        }
        row.setOnClickListener(new View.OnClickListener() {
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
            signedOut.setTextColor(MUTED);
            follows.addView(signedOut);
            return;
        }
        java.util.List<FroglogFollow> people = FroglogSocial.snapshot();
        if (people.isEmpty()) {
            TextView empty = text("No recent activity from people you follow.", 14, false);
            empty.setTextColor(MUTED);
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
        status.setTextColor(MUTED);
        row.addView(status);
        row.setOnClickListener(new View.OnClickListener() {
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
            error.setTextColor(MUTED);
            games.addView(error);
            return;
        }
        if (recent == null || recent.games.isEmpty()) {
            TextView empty = text("No games in " + FroglogGames.filterLabel(filter) + ".", 14, false);
            empty.setTextColor(MUTED);
            games.addView(empty);
            return;
        }
        for (int i = 0; i < recent.games.size(); i++) {
            games.addView(card(gameRow(recent.games.get(i))));
        }
    }

    private View gameRow(final FroglogGame game) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        final ImageView art = new ImageView(this);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        artParams.rightMargin = dp(12);
        art.setLayoutParams(artParams);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(rounded(FroglogTheme.FIELD, dp(12)));
        art.setClipToOutline(true);
        row.addView(art);
        LinearLayout lines = column(0, 0);
        lines.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        lines.addView(text(game.title, 15, true));
        TextView meta = text(game.meta, 12, false);
        meta.setTextColor(MUTED);
        meta.setMaxLines(2);
        lines.addView(meta);
        row.addView(lines);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(FroglogGameDetail.intent(FroglogPodActivity.this, game, true));
            }
        });
        loadArt(art, game.coverUrl);
        return row;
    }

    private void loadArt(final ImageView art, final String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = fetch(url);
                if (bitmap == null) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            art.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        }, "froglog-pod-art").start();
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            if (conn.getResponseCode() >= 400) {
                return null;
            }
            InputStream in = conn.getInputStream();
            try {
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw == null) {
                    return null;
                }
                int max = 256;
                int width = raw.getWidth();
                int height = raw.getHeight();
                if (width <= max && height <= max) {
                    return raw;
                }
                float scale = Math.min(max / (float) width, max / (float) height);
                return Bitmap.createScaledBitmap(raw, Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), true);
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
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
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new ComponentName(this, FroglogRecentWidget.class));
        if (ids.length > 0) {
            new FroglogRecentWidget().onUpdate(this, manager, ids);
        }
        int[] statsIds = manager.getAppWidgetIds(new ComponentName(this, FroglogStatsWidget.class));
        if (statsIds.length > 0) {
            new FroglogStatsWidget().onUpdate(this, manager, statsIds);
        }
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
