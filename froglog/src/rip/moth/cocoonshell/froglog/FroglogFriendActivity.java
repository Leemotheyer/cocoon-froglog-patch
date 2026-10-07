package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/** Public Froglog library for someone the user follows, opened from Cocoon's friends list. */
public class FroglogFriendActivity extends FroglogActivity {
    public static final String EXTRA_USERNAME = "froglog_username";
    public static final String EXTRA_NAME = "froglog_name";
    public static final String EXTRA_STATUS = "froglog_status";


    private LinearLayout games;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        final String username = intent == null ? "" : intent.getStringExtra(EXTRA_USERNAME);
        String name = intent == null ? null : intent.getStringExtra(EXTRA_NAME);
        String status = intent == null ? null : intent.getStringExtra(EXTRA_STATUS);
        if (name == null || name.isEmpty()) {
            name = username == null ? "Froglog" : username;
        }

        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.addView(FroglogTheme.title(this, name));
        if (status != null && !status.isEmpty()) {
            TextView line = text(status, 14, false);
            line.setTextColor(FroglogTheme.MUTED);
            root.addView(line);
        }
        root.addView(gap(16));
        root.addView(section("Public games"));
        games = new LinearLayout(this);
        games.setOrientation(LinearLayout.VERTICAL);
        root.addView(games);
        scroll.addView(root);
        setContentView(scroll);
        paintSystemBars();
        load(username);
    }

    private void load(final String username) {
        games.removeAllViews();
        if (username == null || username.isEmpty()) {
            games.addView(muted("This Froglog profile has no name."));
            return;
        }
        games.addView(muted("Loading their public games…"));
        final String token = FroglogStore.signedIn(this) ? FroglogStore.token(this) : null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FroglogClient.Recent recent = FroglogClient.recentGames(token, username, 16, FroglogGames.FILTER_RECENT);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        show(recent);
                    }
                });
            }
        }, "froglog-follow").start();
    }

    private void show(FroglogClient.Recent recent) {
        games.removeAllViews();
        if (recent != null && recent.error != null) {
            games.addView(muted(recent.error));
            return;
        }
        if (recent == null || recent.games.isEmpty()) {
            games.addView(muted("No public games yet."));
            return;
        }
        for (int i = 0; i < recent.games.size(); i++) {
            games.addView(gameRow(recent.games.get(i)));
        }
    }

    private View gameRow(final FroglogGame game) {
        return FroglogCards.gameRow(this, game.title, game.meta, game.coverUrl, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(FroglogGameDetail.intent(FroglogFriendActivity.this, game, false));
            }
        });
    }

    private void paintSystemBars() {
        FroglogTheme.paintSystemBars(this);
    }

    private TextView section(String value) {
        return FroglogTheme.section(this, value);
    }

    private TextView muted(String value) {
        TextView view = FroglogTheme.text(this, value, 14, false);
        view.setTextColor(FroglogTheme.MUTED);
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
