package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;

/**
 * Shared look for Froglog screens and tiles. Colours follow the Cocoon theme mode,
 * accent, and installed colour pack, read from cocoon_settings.
 */
public final class FroglogTheme {
    public static int PAGE = 0xFFEFEFF3;
    public static int CARD = 0xFFFAFAFA;
    public static int INK = 0xFF212121;
    public static int MUTED = 0xCC212121;
    public static int ACCENT = 0xFF00D0B8;
    public static int ON_ACCENT = Color.WHITE;
    public static int FIELD = 0xFFE6E6EC;
    public static int HINT = 0x80212121;
    public static int LINE = 0x14000000;
    public static int WARN = 0xFFFF9800;
    public static boolean DARK;

    static final String PREFS = "cocoon_settings";

    private FroglogTheme() {}

    /** Accent name, picker start, picker end. Matches eg.c; MINT and COCOON render as FRESH. */
    private static final Object[][] ACCENTS = {
            {"CORAL", 0xFFF890B6, 0xFFFF5757},
            {"DAWN", 0xFFFFC2A2, 0xFFFF8820},
            {"SUNFLOWER", 0xFFFED6AD, 0xFFF3B817},
            {"OCEAN", 0xFF8389FA, 0xFF3140E4},
            {"BREEZE", 0xFF90CCF8, 0xFF57A0FF},
            {"WISTERIA", 0xFF86A7FD, 0xFF8037FF},
            {"ORCHID", 0xFFDA99FF, 0xFFE957FF},
            {"SLATE", 0xFF848C98, 0xFF565E69},
            {"OBSIDIAN", 0xFF3A3A3A, 0xFF2A2A2A},
            {"STORMY", 0xFFC8C8C8, 0xFF989DA1},
            {"FRESH", 0xFF76D788, 0xFF00D0B8},
    };

    /** Reads the current Cocoon theme into the colour fields. Cheap enough to call per screen. */
    public static synchronized void load(Context context) {
        Palette p = resolve(context);
        PAGE = p.page;
        CARD = p.card;
        INK = p.ink;
        MUTED = p.muted;
        ACCENT = p.accent;
        ON_ACCENT = p.onAccent;
        FIELD = p.field;
        HINT = withAlpha(p.ink, 0x80);
        LINE = p.dark ? 0x1FFFFFFF : 0x14000000;
        DARK = p.dark;
    }

    public static final class Palette {
        public int page;
        public int card;
        public int ink;
        public int muted;
        public int accent;
        public int onAccent;
        public int field;
        public boolean dark;
    }

    public static Palette resolve(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String mode = prefs.getString("theme_mode", "SYSTEM");
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean oled = "OLED".equals(mode);
        boolean dark = oled || "DARK".equals(mode) || (!"LIGHT".equals(mode) && night);
        Palette p = new Palette();
        p.dark = dark;
        if (oled) {
            p.page = 0xFF000000;
            p.card = 0xFF1C1C1E;
            p.ink = 0xFFFAFAFA;
            p.field = 0xFF2A2A2E;
        } else if (dark) {
            p.page = 0xFF1E1E1E;
            p.card = 0xFF3A3A3A;
            p.ink = 0xFFFAFAFA;
            p.field = 0xFF4A4A4A;
        } else {
            p.page = 0xFFEFEFF3;
            p.card = 0xFFFAFAFA;
            p.ink = 0xFF212121;
            p.field = 0xFFE6E6EC;
        }
        p.muted = withAlpha(p.ink, 0xB3);
        int[] accent = accent(prefs.getString("accent_theme", "FRESH"));
        if (prefs.getString("custom_theme_colors_id", null) != null) {
            applyCustom(p, prefs.getBoolean("theme_accent_override_enabled", false), accent);
        }
        p.accent = readableAccent(accent[0], accent[1], p.page);
        p.onAccent = luminance(p.accent) > 0.55 ? 0xFF1C1C1E : Color.WHITE;
        return p;
    }

    static int[] accent(String name) {
        String wanted = name == null || "MINT".equals(name) || "COCOON".equals(name) ? "FRESH" : name;
        for (Object[] row : ACCENTS) {
            if (row[0].equals(wanted)) {
                return new int[] {(Integer) row[1], (Integer) row[2]};
            }
        }
        return new int[] {0xFF76D788, 0xFF00D0B8};
    }

    /** Uses whichever end of the accent gradient stands out more against the page. */
    static int readableAccent(int start, int end, int page) {
        return contrast(start, page) >= contrast(end, page) ? start : end;
    }

    /**
     * Cocoon keeps the active colour pack in eg.j0.j (StateFlow of CustomTheme). Only present
     * in the Cocoon process once the theme engine has loaded; otherwise built-ins stay.
     */
    private static void applyCustom(Palette p, boolean packAccent, int[] accent) {
        try {
            Field flowField = Class.forName("eg.j0").getField("j");
            Object flow = flowField.get(null);
            Object theme = flow.getClass().getMethod("getValue").invoke(flow);
            if (theme == null) {
                return;
            }
            Object scheme = theme.getClass().getMethod("getColorScheme").invoke(theme);
            if (scheme == null) {
                return;
            }
            Integer page = schemeColor(scheme, "backgroundGradientEnd");
            Integer card = schemeColor(scheme, "cardGradientStart");
            Integer ink = schemeColor(scheme, "textPrimary");
            Integer muted = schemeColor(scheme, "textSecondary");
            Integer tile = schemeColor(scheme, "tileBorder");
            if (page != null) {
                p.page = page | 0xFF000000;
                p.dark = luminance(p.page) < 0.4;
            }
            if (card != null) {
                p.card = card | 0xFF000000;
            }
            if (ink != null) {
                p.ink = ink;
            }
            p.muted = muted != null ? muted : withAlpha(p.ink, 0xB3);
            p.field = tile != null ? (tile | 0xFF000000) : blend(p.card, p.ink, 0.08f);
            if (packAccent) {
                Integer start = schemeColor(scheme, "accentGradientStart");
                Integer end = schemeColor(scheme, "accentGradientEnd");
                if (start != null) {
                    accent[0] = start;
                    accent[1] = end != null ? end : start;
                }
            }
        } catch (Throwable ignored) {
            // Built-in palette stays.
        }
    }

    /** ResolvedColorScheme stores Compose w1.v colours; sRGB packs ARGB into the top 32 bits. */
    private static Integer schemeColor(Object scheme, String name) {
        try {
            Field field = scheme.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object color = field.get(scheme);
            if (color == null) {
                return null;
            }
            for (Field inner : color.getClass().getDeclaredFields()) {
                if (inner.getType() == long.class && !java.lang.reflect.Modifier.isStatic(inner.getModifiers())) {
                    inner.setAccessible(true);
                    return (int) (inner.getLong(color) >>> 32);
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int blend(int from, int to, float amount) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.rgb(r, g, b);
    }

    static double luminance(int color) {
        return (0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color))
                + 0.0722 * channel(Color.blue(color)));
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double contrast(int a, int b) {
        double la = luminance(a) + 0.05;
        double lb = luminance(b) + 0.05;
        return la > lb ? la / lb : lb / la;
    }

    public static Typeface font(Context context, boolean bold) {
        String name = bold ? "poppins_semibold" : "poppins_regular";
        int id = context.getResources().getIdentifier(name, "font", context.getPackageName());
        if (id != 0 && Build.VERSION.SDK_INT >= 26) {
            try {
                return context.getResources().getFont(id);
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
    }

    public static void paintSystemBars(Activity activity) {
        activity.getWindow().setStatusBarColor(PAGE);
        activity.getWindow().setNavigationBarColor(PAGE);
        if (Build.VERSION.SDK_INT < 30) {
            return;
        }
        View decor = activity.getWindow().peekDecorView();
        if (decor == null) {
            return;
        }
        WindowInsetsController controller = decor.getWindowInsetsController();
        if (controller != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(DARK ? 0 : light, light);
        }
    }

    public static void page(View view) {
        view.setBackgroundColor(PAGE);
    }

    public static TextView title(Context context, String value) {
        TextView view = text(context, value, 26, true);
        view.setTextColor(INK);
        return view;
    }

    public static TextView section(Context context, String value) {
        TextView view = text(context, value.toUpperCase(java.util.Locale.ROOT), 12, true);
        view.setTextColor(MUTED);
        view.setLetterSpacing(0.08f);
        view.setPadding(0, 0, 0, dp(context, 4));
        return view;
    }

    public static TextView text(Context context, String value, int sp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(INK);
        view.setTypeface(font(context, bold));
        return view;
    }

    public static TextView muted(Context context, String value, int sp) {
        TextView view = text(context, value, sp, false);
        view.setTextColor(MUTED);
        return view;
    }

    public static EditText field(Context context, String hint) {
        EditText view = new EditText(context);
        view.setHint(hint);
        view.setHintTextColor(HINT);
        view.setTextColor(INK);
        view.setTypeface(font(context, false));
        view.setSingleLine(true);
        view.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        view.setBackground(rounded(FIELD, dp(context, 14)));
        return view;
    }

    public static Button button(Context context, String label, View.OnClickListener listener) {
        Button view = new Button(context);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextColor(ON_ACCENT);
        view.setTypeface(font(context, true));
        view.setBackground(focusable(context, rounded(ACCENT, dp(context, 16)), 16));
        view.setOnClickListener(listener);
        view.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        return view;
    }

    public static Button secondary(Context context, String label, View.OnClickListener listener) {
        Button view = button(context, label, listener);
        view.setTextColor(INK);
        view.setBackground(focusable(context, rounded(FIELD, dp(context, 16)), 16));
        return view;
    }

    /** Small rounded label, e.g. a status or platform tag. */
    public static TextView chip(Context context, String value, boolean strong) {
        TextView view = text(context, value, 11, true);
        view.setTextColor(strong ? ON_ACCENT : INK);
        view.setBackground(rounded(strong ? ACCENT : FIELD, dp(context, 10)));
        view.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        return view;
    }

    public static LinearLayout card(Context context, View child) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(shadowCard(context, 20));
        wrap.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 12);
        wrap.setLayoutParams(params);
        wrap.addView(child);
        return wrap;
    }

    public static LayerDrawable shadowCard(Context context, int radiusDp) {
        int radius = dp(context, radiusDp);
        GradientDrawable shadow = rounded(DARK ? 0x40000000 : 0x18000000, radius);
        GradientDrawable face = rounded(CARD, radius);
        LayerDrawable layers = new LayerDrawable(new GradientDrawable[] { shadow, face });
        layers.setLayerInset(0, dp(context, 1), dp(context, 2), dp(context, 1), 0);
        layers.setLayerInset(1, 0, 0, 0, dp(context, 3));
        return layers;
    }

    /** Wraps a background with an accent ring while the view has controller focus. */
    public static android.graphics.drawable.Drawable focusable(Context context,
            android.graphics.drawable.Drawable base, int radiusDp) {
        GradientDrawable ring = rounded(0x00000000, dp(context, radiusDp));
        ring.setStroke(dp(context, 2), ACCENT);
        LayerDrawable focused = new LayerDrawable(new android.graphics.drawable.Drawable[] {
                base.getConstantState() == null ? base : base.getConstantState().newDrawable().mutate(), ring });
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[] { android.R.attr.state_focused }, focused);
        states.addState(new int[] { android.R.attr.state_pressed }, focused);
        states.addState(new int[0], base);
        return states;
    }

    /** Makes a tappable row reachable with the d-pad and shows a ring while focused. */
    public static void row(View view, View.OnClickListener listener) {
        Context context = view.getContext();
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnClickListener(listener);
        android.graphics.drawable.Drawable base = view.getBackground();
        if (base == null) {
            base = rounded(0x00000000, dp(context, 14));
        }
        view.setBackground(focusable(context, base, 14));
    }

    public static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public static GradientDrawable pill(int color, Context context) {
        return rounded(color, dp(context, 20));
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
