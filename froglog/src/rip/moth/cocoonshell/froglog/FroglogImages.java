package rip.moth.cocoonshell.froglog;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cover downloads shared by the Froglog screens, with a small memory cache keyed by URL and size. */
public final class FroglogImages {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FroglogImages() {}

    /** Loads {@code url} into {@code view}, ignoring the result if the view was reused for another URL. */
    public static void into(final ImageView view, final String url, final int maxPx) {
        view.setTag(url);
        if (url == null || url.isEmpty()) {
            return;
        }
        Bitmap cached = CACHE.get(url + "@" + maxPx);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = fetch(url, maxPx);
                if (bitmap == null) {
                    return;
                }
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(view.getTag())) {
                            view.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
    }

    /** Blocking fetch, scaled so the longer side is at most {@code maxPx}. */
    public static Bitmap fetch(String url, int maxPx) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String key = url + "@" + maxPx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = download(url);
        if (bytes == null) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) {
            sample *= 2;
        }
        options.inSampleSize = sample;
        Bitmap raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (raw == null) {
            return null;
        }
        int width = raw.getWidth();
        int height = raw.getHeight();
        Bitmap result = raw;
        if (width > maxPx || height > maxPx) {
            float scale = Math.min(maxPx / (float) width, maxPx / (float) height);
            result = Bitmap.createScaledBitmap(raw, Math.max(1, (int) (width * scale)),
                    Math.max(1, (int) (height * scale)), true);
        }
        CACHE.put(key, result);
        return result;
    }

    static byte[] download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "CocoonFroglogWidget/1.0");
            if (conn.getResponseCode() >= 400) {
                return null;
            }
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > 12 * 1024 * 1024) {
                        return null;
                    }
                }
                return out.toByteArray();
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
}
