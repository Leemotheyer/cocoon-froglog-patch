package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

        LinearLayout root = column();
        TextView heading = text(title, 22, true);
        TextView line = text(meta.isEmpty() ? "No session yet" : meta, 14, false);
        line.setTextColor(Color.parseColor("#C8C2B8"));
        final ImageView art = new ImageView(this);
        art.setAdjustViewBounds(true);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        artParams.bottomMargin = dp(12);
        art.setLayoutParams(artParams);
        TextView body = text(review.isEmpty() ? "No review on this game." : review, 15, false);
        root.addView(heading);
        root.addView(gap());
        root.addView(line);
        root.addView(gap());
        root.addView(art);
        root.addView(body);
        setContentView(root);
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

    private LinearLayout column() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.parseColor("#121418"));
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

    private View gap() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
