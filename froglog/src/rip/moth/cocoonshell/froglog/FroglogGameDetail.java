package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** A small cover beside the title, then every field Froglog has for the game, its review, and sessions. */
public class FroglogGameDetail extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_META = "meta";
    public static final String EXTRA_REVIEW = "review";
    public static final String EXTRA_COVER = "cover";
    public static final String EXTRA_JSON = "json";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_LIVE = "live";
    /** True when the game is in the signed-in user's library, so its sessions can be read. */
    public static final String EXTRA_OWN = "own";

    private static final int SESSIONS_SHOWN = 20;

    public static Intent intent(Context context, FroglogGame game, boolean own) {
        Intent detail = new Intent(context, FroglogGameDetail.class);
        detail.putExtra(EXTRA_TITLE, game.title);
        detail.putExtra(EXTRA_META, game.meta);
        detail.putExtra(EXTRA_REVIEW, game.review);
        detail.putExtra(EXTRA_COVER, game.coverUrl == null ? "" : game.coverUrl);
        detail.putExtra(EXTRA_JSON, game.json == null ? "" : game.json);
        detail.putExtra(EXTRA_ID, game.id);
        detail.putExtra(EXTRA_LIVE, game.live);
        detail.putExtra(EXTRA_OWN, own);
        return detail;
    }

    private LinearLayout sessionList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = extra(EXTRA_TITLE, "Froglog");
        String meta = extra(EXTRA_META, "");
        String review = extra(EXTRA_REVIEW, "");
        final String cover = extra(EXTRA_COVER, "");
        JSONObject game = parse(extra(EXTRA_JSON, ""));
        Intent intent = getIntent();
        final long id = intent == null ? -1 : intent.getLongExtra(EXTRA_ID, -1);
        final boolean live = intent != null && intent.getBooleanExtra(EXTRA_LIVE, false);
        boolean own = intent != null && intent.getBooleanExtra(EXTRA_OWN, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView art = new ImageView(this);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(88), dp(116));
        artParams.rightMargin = dp(16);
        art.setLayoutParams(artParams);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(FroglogTheme.rounded(FroglogTheme.FIELD, dp(12)));
        art.setClipToOutline(true);
        header.addView(art);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heading.addView(FroglogTheme.title(this, title));
        TextView line = FroglogTheme.text(this, meta.isEmpty() ? "No session yet" : meta, 14, false);
        line.setTextColor(FroglogTheme.MUTED);
        heading.addView(line);
        header.addView(heading);
        root.addView(FroglogTheme.card(this, header));

        List<String[]> rows = FroglogGameInfo.rows(game);
        if (!rows.isEmpty()) {
            LinearLayout details = column();
            details.addView(FroglogTheme.section(this, "Details"));
            for (String[] row : rows) {
                details.addView(field(row[0], row[1]));
            }
            root.addView(spaced(FroglogTheme.card(this, details)));
        }

        LinearLayout reviewBody = column();
        reviewBody.addView(FroglogTheme.section(this, "Review"));
        reviewBody.addView(FroglogTheme.text(this, review.isEmpty() ? "No review on this game." : review, 15, false));
        root.addView(spaced(FroglogTheme.card(this, reviewBody)));

        final String token = own && id > 0 ? FroglogStore.token(this) : null;
        if (token != null && !token.isEmpty()) {
            sessionList = column();
            sessionList.addView(FroglogTheme.section(this, "Sessions"));
            sessionList.addView(muted("Loading sessions…"));
            root.addView(spaced(FroglogTheme.card(this, sessionList)));
            loadSessions(token, live, id);
        }

        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        scroll.addView(root);
        setContentView(scroll);
        FroglogTheme.paintSystemBars(this);
        if (!cover.isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = load(cover);
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
            }, "froglog-detail").start();
        }
    }

    private void loadSessions(final String token, final boolean live, final long id) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String[]> found = null;
                String error = null;
                try {
                    found = FroglogGameInfo.sessions(FroglogClient.gameSessions(token, live, id));
                } catch (Exception e) {
                    error = e.getMessage() == null ? "Could not load the sessions" : e.getMessage();
                }
                final List<String[]> sessions = found;
                final String failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            showSessions(sessions, failure);
                        }
                    }
                });
            }
        }, "froglog-detail-sessions").start();
    }

    private void showSessions(List<String[]> sessions, String error) {
        while (sessionList.getChildCount() > 1) {
            sessionList.removeViewAt(1);
        }
        if (error != null) {
            sessionList.addView(muted(error));
            return;
        }
        if (sessions.isEmpty()) {
            sessionList.addView(muted("No sessions logged yet."));
            return;
        }
        int shown = Math.min(sessions.size(), SESSIONS_SHOWN);
        for (int i = 0; i < shown; i++) {
            String[] session = sessions.get(i);
            String when = session[0].isEmpty() ? "Undated" : session[0];
            sessionList.addView(field(when, session[1].isEmpty() ? "—" : session[1]));
            if (!session[2].isEmpty()) {
                TextView notes = muted(session[2]);
                notes.setPadding(0, 0, 0, dp(6));
                sessionList.addView(notes);
            }
        }
        if (sessions.size() > shown) {
            sessionList.addView(muted("+" + (sessions.size() - shown) + " older sessions"));
        }
    }

    private View field(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView name = FroglogTheme.text(this, label, 14, false);
        name.setTextColor(FroglogTheme.MUTED);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(name);
        TextView shown = FroglogTheme.text(this, value, 14, true);
        shown.setGravity(Gravity.END);
        shown.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));
        row.addView(shown);
        return row;
    }

    private TextView muted(String value) {
        TextView text = FroglogTheme.text(this, value, 13, false);
        text.setTextColor(FroglogTheme.MUTED);
        return text;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private View spaced(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        card.setLayoutParams(params);
        return card;
    }

    private static JSONObject parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extra(String key, String fallback) {
        String value = getIntent() == null ? null : getIntent().getStringExtra(key);
        return value == null ? fallback : value;
    }

    private static Bitmap load(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            conn.connect();
            if (conn.getResponseCode() >= 400) {
                return null;
            }
            InputStream in = conn.getInputStream();
            try {
                return BitmapFactory.decodeStream(in);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
