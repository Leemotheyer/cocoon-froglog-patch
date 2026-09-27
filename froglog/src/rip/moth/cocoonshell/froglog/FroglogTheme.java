package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Context;
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

/**
 * Shared look for Froglog screens and tiles. Matches Cocoon 3: Poppins, white
 * shadowed cards, light page, mint accent.
 */
public final class FroglogTheme {
    public static final int PAGE = 0xFFF3F4F7;
    public static final int CARD = 0xFFFFFFFF;
    public static final int INK = 0xFF1C1C1E;
    public static final int MUTED = 0xFF8E8E93;
    public static final int ACCENT = 0xFF3BCF7A;
    public static final int FIELD = 0xFFF2F3F5;
    public static final int HINT = 0xFFAEAEB2;
    public static final int LINE = 0x14000000;

    private FroglogTheme() {}

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
            controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
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
        TextView view = text(context, value, 13, true);
        view.setTextColor(MUTED);
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
        view.setTextColor(Color.WHITE);
        view.setTypeface(font(context, true));
        view.setBackground(rounded(ACCENT, dp(context, 16)));
        view.setOnClickListener(listener);
        view.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        return view;
    }

    public static Button secondary(Context context, String label, View.OnClickListener listener) {
        Button view = button(context, label, listener);
        view.setTextColor(INK);
        view.setBackground(rounded(FIELD, dp(context, 16)));
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
        GradientDrawable shadow = rounded(0x18000000, radius);
        GradientDrawable face = rounded(CARD, radius);
        LayerDrawable layers = new LayerDrawable(new GradientDrawable[] { shadow, face });
        layers.setLayerInset(0, dp(context, 1), dp(context, 2), dp(context, 1), 0);
        layers.setLayerInset(1, 0, 0, 0, dp(context, 3));
        return layers;
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
