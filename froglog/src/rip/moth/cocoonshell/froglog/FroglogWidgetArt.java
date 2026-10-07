package rip.moth.cocoonshell.froglog;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;

/**
 * Sizes and bitmaps shared by the Froglog widgets, following Cocoon's own widget metrics:
 * a 96dp grid pitch is scale 1.0, a tile is 0.8 of its pitch, and list covers are square
 * with a corner of about a seventh of their side.
 */
final class FroglogWidgetArt {
    /** Space {@link GlassCompat#styleAndroidWidget} keeps around the themed tile face. */
    private static final int HALO_DP = 5;
    private static final int DROP_DP = 3;

    private FroglogWidgetArt() {}

    static final class Box {
        /** Inside the tile face and its padding. */
        int widthDp;
        int heightDp;
        int padH;
        int padV;
        float scale;
    }

    static Box box(Bundle options, int fallbackW, int fallbackH) {
        int width = 0;
        int height = 0;
        if (options != null) {
            width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
            height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (width <= 0) {
                width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            }
            if (height <= 0) {
                height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            }
        }
        if (width <= 0) {
            width = fallbackW;
        }
        if (height <= 0) {
            height = fallbackH;
        }
        int rows = Math.max(1, Math.round(height / 96f + 0.2f));
        float pitch = height / (rows - 0.2f);
        Box box = new Box();
        box.scale = clamp(pitch / 96f, 0.8f, 1.3f);
        box.padH = Math.round(clamp(10 * box.scale, 8, 16));
        box.padV = Math.round(clamp(8 * box.scale, 6, 14));
        box.widthDp = Math.max(24, width - 2 * HALO_DP - 2 * box.padH);
        box.heightDp = Math.max(24, height - 2 * HALO_DP - DROP_DP - 2 * box.padV);
        return box;
    }

    static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    static int px(Context context, float dp) {
        return Math.max(1, Math.round(dp * context.getResources().getDisplayMetrics().density));
    }

    /** Centre-cropped square with rounded corners, or a plain rounded square when there is no art. */
    static Bitmap cover(Context context, Bitmap source, int sizeDp, int fallbackColor) {
        int size = px(context, sizeDp);
        float radius = size * 0.15f;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF rect = new RectF(0, 0, size, size);
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            paint.setColor(fallbackColor);
            canvas.drawRoundRect(rect, radius, radius, paint);
            return out;
        }
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(size / (float) source.getWidth(), size / (float) source.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((size - source.getWidth() * scale) / 2f, (size - source.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawRoundRect(rect, radius, radius, paint);
        return out;
    }

    /**
     * Cocoon Playtime-style bars: one rounded bar per day on a faint track, today in the
     * accent, the busiest day labelled, weekday letters underneath when there is room.
     */
    static Bitmap bars(Context context, double[] hours, String[] letters, int widthDp, int heightDp,
            float scale, FroglogTheme.Palette palette) {
        float density = context.getResources().getDisplayMetrics().density;
        int width = px(context, widthDp);
        int height = px(context, heightDp);
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int count = hours.length;
        if (count == 0) {
            return out;
        }
        Canvas canvas = new Canvas(out);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(font(context));
        text.setTextAlign(Paint.Align.CENTER);
        float letterSize = clamp(10 * scale, 8, 13) * density;
        boolean showLetters = heightDp >= 40 && letters != null && letters.length == count;
        float letterBand = showLetters ? letterSize + clamp(5 * scale, 4, 8) * density : 0;
        float peakSize = clamp(9 * scale, 8, 12) * density;
        double max = 0;
        int peak = -1;
        for (int i = 0; i < count; i++) {
            if (hours[i] > max) {
                max = hours[i];
                peak = i;
            }
        }
        boolean showPeak = peak >= 0 && heightDp >= 48;
        float top = showPeak ? peakSize + 3 * density : 0;
        float bottom = height - letterBand;
        float barArea = Math.max(1, bottom - top);
        float slot = width / (float) count;
        float barWidth = Math.min(slot * 0.62f, clamp(22 * scale, 10, 34) * density);
        float radius = barWidth * 0.4f;
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setColor(withAlpha(palette.muted, 0x24));
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < count; i++) {
            float cx = slot * i + slot / 2f;
            RectF lane = new RectF(cx - barWidth / 2f, top, cx + barWidth / 2f, bottom);
            canvas.drawRoundRect(lane, radius, radius, track);
            if (hours[i] > 0 && max > 0) {
                float barHeight = Math.max(radius * 2f, (float) (barArea * hours[i] / max));
                RectF bar = new RectF(lane.left, bottom - barHeight, lane.right, bottom);
                fill.setColor(i == count - 1 ? palette.accent : withAlpha(palette.accent, 0x8C));
                canvas.drawRoundRect(bar, radius, radius, fill);
            }
            if (showLetters) {
                text.setTextSize(letterSize);
                text.setColor(i == count - 1 ? palette.accent : palette.muted);
                canvas.drawText(letters[i], cx, height - text.descent(), text);
            }
        }
        if (showPeak) {
            text.setTextSize(peakSize);
            text.setColor(palette.ink);
            float cx = slot * peak + slot / 2f;
            String label = FroglogGames.duration(max);
            float half = text.measureText(label) / 2f;
            cx = Math.max(half, Math.min(width - half, cx));
            canvas.drawText(label, cx, top - 3 * density - text.descent(), text);
        }
        return out;
    }

    private static Typeface font(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                int id = context.getResources().getIdentifier("poppins_semibold", "font", context.getPackageName());
                if (id != 0) {
                    Typeface face = context.getResources().getFont(id);
                    if (face != null) {
                        return face;
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall back to the system bold face.
            }
        }
        return Typeface.DEFAULT_BOLD;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
