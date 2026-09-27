package rip.moth.cocoonshell.froglog;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

/**
 * Glass / RuntimeShader needs API 33 (Android 13). On BlueStacks 9 the default
 * theme still asks for glass and the process dies in {@code kf.n2.b}. These
 * helpers force the solid fallback below API 33 and leave Android 13+ unchanged.
 */
public final class GlassCompat {
    private static final String SOLID = "solid";

    private GlassCompat() {}

    public static boolean runtimeShadersAvailable() {
        return Build.VERSION.SDK_INT >= 33;
    }

    public static String safeSurfaceMaterial(String material) {
        if (runtimeShadersAvailable()) {
            return material;
        }
        return SOLID;
    }

    public static Boolean safeGlassOnTiles(Boolean value) {
        if (runtimeShadersAvailable()) {
            return value;
        }
        return Boolean.FALSE;
    }

    public static String safeSurfaceName(String name) {
        if (runtimeShadersAvailable() || name == null) {
            return name;
        }
        if ("GLASS".equalsIgnoreCase(name) || "BLURRED".equalsIgnoreCase(name)) {
            return "SOLID";
        }
        return name;
    }

    /**
     * Compose tile chrome is torn down while the home screen pans, which makes
     * Android-widget shadows blink. Paint a Recently played-style squircle and
     * an in-bounds drop shadow on the host so both stay put.
     */
    public static void styleAndroidWidget(View view) {
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            group.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            group.setClipChildren(false);
            group.setClipToPadding(false);
        }
        view.setClickable(false);
        view.setElevation(0f);
        view.setOutlineProvider(null);
        view.setClipToOutline(false);
        float density = view.getResources().getDisplayMetrics().density;
        int radius = Math.round(24f * density);
        int halo = Math.round(5f * density);
        int drop = Math.round(3f * density);
        GradientDrawable shade = new GradientDrawable();
        shade.setColor(0x1A212121);
        shade.setCornerRadius(radius);
        GradientDrawable face = new GradientDrawable();
        face.setColor(0xFFFFFFFF);
        face.setCornerRadius(radius);
        LayerDrawable layers = new LayerDrawable(new GradientDrawable[] { shade, face });
        layers.setLayerInset(0, halo, halo + drop, halo, 0);
        layers.setLayerInset(1, halo, halo, halo, halo + drop);
        view.setBackground(layers);
        view.setPadding(halo, halo, halo, halo + drop);
    }
}
