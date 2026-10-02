package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Public Froglog library for someone the user follows, opened from Cocoon's friends list. */
public class FroglogFriendActivity extends Activity {
    public static final String EXTRA_USERNAME = "froglog_username";
    public static final String EXTRA_NAME = "froglog_name";
    public static final String EXTRA_STATUS = "froglog_status";

    private static final int INK = FroglogTheme.INK;
    private static final int CARD = FroglogTheme.CARD;
    private static final int CREAM = FroglogTheme.INK;
    private static final int MUTED = FroglogTheme.MUTED;
    private static final int GREEN = FroglogTheme.ACCENT;

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
            line.setTextColor(MUTED);
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
            games.addView(FroglogTheme.card(this, gameRow(recent.games.get(i))));
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
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        lines.addView(text(game.title, 15, true));
        TextView meta = text(game.meta, 12, false);
        meta.setTextColor(MUTED);
        lines.addView(meta);
        row.addView(lines);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(FroglogGameDetail.intent(FroglogFriendActivity.this, game, false));
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
        }, "froglog-follow-art").start();
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
                if (raw.getWidth() <= max && raw.getHeight() <= max) {
                    return raw;
                }
                float scale = Math.min(max / (float) raw.getWidth(), max / (float) raw.getHeight());
                return Bitmap.createScaledBitmap(raw, Math.max(1, (int) (raw.getWidth() * scale)),
                        Math.max(1, (int) (raw.getHeight() * scale)), true);
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

    private void paintSystemBars() {
        FroglogTheme.paintSystemBars(this);
    }

    private TextView section(String value) {
        return FroglogTheme.section(this, value);
    }

    private TextView muted(String value) {
        TextView view = FroglogTheme.text(this, value, 14, false);
        view.setTextColor(MUTED);
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
