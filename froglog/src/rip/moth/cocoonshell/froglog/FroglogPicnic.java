package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import jb.a;

/** Picnic screenshot upload entry from Cocoon's info dialog (cf.pi.Y). */
public final class FroglogPicnic {
    private static final String TAG = "FroglogPicnic";
    private FroglogPicnic() {}

    public static a uploadAction(Context context, Object picnicDetail) {
        return new FroglogPicnicUpload(context, picnicDetail);
    }

    public static void openUpload(Context context, Object picnicDetail) {
        if (context == null || picnicDetail == null) {
            return;
        }
        try {
            Object record = field(picnicDetail, "a");
            Object game = field(picnicDetail, "b");
            if (record == null) {
                return;
            }
            String uri = stringMethod(record, "getScreenshotUri");
            if (uri == null || uri.isEmpty()) {
                return;
            }
            String mime = stringMethod(record, "getMimeType");
            String display = stringMethod(record, "getDisplayName");
            String recordName = stringMethod(record, "getGameName");
            String platform = stringMethod(record, "getPlatformId");
            String title = "";
            String alt = "";
            if (game != null) {
                title = trim(stringMethod(game, "getTitle"));
                alt = trim(stringMethod(game, "getDisplayName"));
            }
            if (title.isEmpty()) {
                title = !alt.isEmpty() ? alt : trim(recordName);
            }
            if (alt.isEmpty() || alt.equals(title)) {
                alt = !trim(recordName).equals(title) ? trim(recordName) : "";
            }
            Intent intent = new Intent(context, FroglogPicnicActivity.class)
                    .putExtra(FroglogPicnicActivity.EXTRA_URI, uri)
                    .putExtra(FroglogPicnicActivity.EXTRA_MIME, nullToEmpty(mime))
                    .putExtra(FroglogPicnicActivity.EXTRA_NAME, nullToEmpty(display))
                    .putExtra(FroglogPicnicActivity.EXTRA_TITLE, title)
                    .putExtra(FroglogPicnicActivity.EXTRA_ALT_TITLE, alt)
                    .putExtra(FroglogPicnicActivity.EXTRA_PLATFORM, nullToEmpty(platform))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception error) {
            Log.w(TAG, "could not open picnic upload", error);
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getField(name);
        return field.get(owner);
    }

    private static String stringMethod(Object owner, String name) throws Exception {
        Method method = owner.getClass().getMethod(name);
        Object value = method.invoke(owner);
        return value instanceof String ? (String) value : "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
