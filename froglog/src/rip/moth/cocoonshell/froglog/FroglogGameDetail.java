package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Cover, hours, last session, and review for one Froglog game. */
public class FroglogGameDetail extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_META = "meta";
    public static final String EXTRA_REVIEW = "review";
    public static final String EXTRA_COVER = "cover";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = extra(EXTRA_TITLE, "Froglog");
        String meta = extra(EXTRA_META, "");
        String review = extra(EXTRA_REVIEW, "");
        final String cover = extra(EXTRA_COVER, "");

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(FroglogTheme.title(this, title));
        TextView line = FroglogTheme.text(this, meta.isEmpty() ? "No session yet" : meta, 14, false);
        line.setTextColor(FroglogTheme.MUTED);
        body.addView(line);
        final ImageView art = new ImageView(this);
        art.setAdjustViewBounds(true);
        art.setBackground(FroglogTheme.rounded(FroglogTheme.FIELD, dp(16)));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        artParams.topMargin = dp(16);
        artParams.bottomMargin = dp(16);
        art.setLayoutParams(artParams);
        body.addView(art);
        body.addView(FroglogTheme.section(this, "Review"));
        body.addView(FroglogTheme.text(this, review.isEmpty() ? "No review on this game." : review, 15, false));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.addView(FroglogTheme.card(this, body));
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
                            art.setImageBitmap(bitmap);
                        }
                    });
                }
            }, "froglog-detail").start();
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
