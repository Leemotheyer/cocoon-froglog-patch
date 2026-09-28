package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Froglog avatars can be animated GIFs. Cocoon's friend rows draw a still image, so each
 * avatar is fetched once and stored as a PNG of its first frame, then passed as a file URI.
 */
public final class FroglogAvatars {
    private static final String TAG = "FroglogWidget";
    private static final long FRESH_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SIDE = 256;

    private FroglogAvatars() {}

    /** Same people with avatars swapped for local still images. Must run off the main thread. */
    public static List<FroglogFollow> localize(Context context, List<FroglogFollow> people) {
        if (context == null || people == null || people.isEmpty()) {
            return people;
        }
        ArrayList<FroglogFollow> out = new ArrayList<FroglogFollow>(people.size());
        for (int i = 0; i < people.size(); i++) {
            FroglogFollow p = people.get(i);
            String avatar = still(context, p.avatarUrl);
            out.add(avatar == null || avatar.equals(p.avatarUrl) ? p
                    : new FroglogFollow(p.username, p.name, avatar, p.status, p.game, p.playing));
        }
        return out;
    }

    static String still(Context context, String url) {
        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            return url;
        }
        File dir = new File(context.getCacheDir(), "froglog-avatars");
        File file = new File(dir, hash(url) + ".png");
        if (file.isFile() && file.length() > 0 && System.currentTimeMillis() - file.lastModified() < FRESH_MS) {
            return Uri.fromFile(file).toString();
        }
        try {
            byte[] bytes = download(url);
            Bitmap bitmap = decode(bytes);
            if (bitmap == null) {
                return file.isFile() ? Uri.fromFile(file).toString() : url;
            }
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return url;
            }
            File tmp = new File(dir, file.getName() + ".tmp");
            FileOutputStream stream = new FileOutputStream(tmp);
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            } finally {
                stream.close();
                bitmap.recycle();
            }
            if (!tmp.renameTo(file)) {
                file.delete();
                if (!tmp.renameTo(file)) {
                    return url;
                }
            }
            return Uri.fromFile(file).toString();
        } catch (Exception e) {
            Log.w(TAG, "Could not cache Froglog avatar " + url + ": " + e.getMessage());
            return file.isFile() ? Uri.fromFile(file).toString() : url;
        }
    }

    /** BitmapFactory reads only the first frame of a GIF, which is the still we want. */
    private static Bitmap decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        int side = Math.max(bounds.outWidth, bounds.outHeight);
        options.inSampleSize = 1;
        while (side / (options.inSampleSize * 2) >= MAX_SIDE) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "CocoonFroglogWidget/1.0");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) {
                        throw new IllegalStateException("avatar too large");
                    }
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String hash(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(url.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                out.append(String.format(Locale.US, "%02x", digest[i] & 0xff));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }
}
