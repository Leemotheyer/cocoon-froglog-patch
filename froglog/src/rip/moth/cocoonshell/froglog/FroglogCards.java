package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Game rows with cover art, and the detail card shown before a game is picked. */
public final class FroglogCards {
    private FroglogCards() {}

    /** What the detail card shows. Any field may be empty. */
    public static final class Detail {
        public String title = "";
        public String subtitle = "";
        public String coverUrl;
        public String heroUrl;
        public String description;
        public final List<String> tags = new ArrayList<String>();
        public final List<String[]> rows = new ArrayList<String[]>();
        public String note;

        public static Detail of(FroglogClient.Hit hit) {
            Detail d = new Detail();
            d.title = hit.title;
            d.subtitle = hit.released != null && hit.released.length() >= 4 ? hit.released.substring(0, 4) : "";
            d.coverUrl = hit.portraitUrl != null ? hit.portraitUrl : hit.coverUrl;
            d.heroUrl = hit.heroUrl;
            d.description = hit.description;
            if (hit.platform != null) {
                d.tags.add(hit.platform);
            }
            d.rows.addAll(hit.rows());
            return d;
        }

        public static Detail of(FroglogGame game) {
            Detail d = new Detail();
            d.title = game.title;
            d.subtitle = game.meta == null ? "" : game.meta;
            d.coverUrl = game.coverUrl;
            if (game.live) {
                d.tags.add("Live service");
            }
            if (game.status != null && !game.status.isEmpty()) {
                d.tags.add(game.status);
            }
            if (game.platform != null && !game.platform.isEmpty()) {
                d.tags.add(game.platform);
            }
            org.json.JSONObject json = null;
            try {
                json = game.json == null || game.json.isEmpty() ? null : new org.json.JSONObject(game.json);
            } catch (Exception ignored) {
                // Built locally.
            }
            if (json != null) {
                String hero = json.optString("img", "");
                d.heroUrl = hero.isEmpty() || hero.equals(game.coverUrl) || "null".equals(hero) ? null : hero;
                String description = json.optString("description", "");
                d.description = description.isEmpty() || "null".equals(description) ? null : description;
                d.rows.addAll(FroglogGameInfo.rows(json));
            }
            if (!game.review.isEmpty()) {
                d.rows.add(new String[] {"Review", game.review});
            }
            return d;
        }
    }

    /** A card with a cover on the left, title and meta lines on the right. */
    public static LinearLayout gameRow(Context context, String title, String meta, String coverUrl,
            View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(cover(context, coverUrl, 46, 62));
        LinearLayout lines = new LinearLayout(context);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = FroglogTheme.text(context, title, 15, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        lines.addView(name);
        if (meta != null && !meta.isEmpty()) {
            TextView line = FroglogTheme.muted(context, meta, 12);
            line.setMaxLines(2);
            line.setEllipsize(TextUtils.TruncateAt.END);
            lines.addView(line);
        }
        row.addView(lines);
        if (listener != null) {
            TextView chevron = FroglogTheme.muted(context, "›", 22);
            chevron.setPadding(FroglogTheme.dp(context, 8), 0, 0, 0);
            row.addView(chevron);
        }
        LinearLayout card = FroglogTheme.card(context, row);
        card.setPadding(FroglogTheme.dp(context, 12), FroglogTheme.dp(context, 10),
                FroglogTheme.dp(context, 12), FroglogTheme.dp(context, 10));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        params.topMargin = FroglogTheme.dp(context, 8);
        if (listener != null) {
            FroglogTheme.row(card, listener);
        }
        return card;
    }

    public static ImageView cover(Context context, String url, int widthDp, int heightDp) {
        ImageView art = new ImageView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                FroglogTheme.dp(context, widthDp), FroglogTheme.dp(context, heightDp));
        params.rightMargin = FroglogTheme.dp(context, 12);
        art.setLayoutParams(params);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(FroglogTheme.rounded(FroglogTheme.FIELD, FroglogTheme.dp(context, 10)));
        art.setClipToOutline(true);
        FroglogImages.into(art, url, FroglogTheme.dp(context, Math.max(widthDp, heightDp)));
        return art;
    }

    /** One button on the detail card. The first action is the highlighted confirm. */
    public static final class Action {
        final String label;
        final Runnable run;

        public Action(String label, Runnable run) {
            this.label = label;
            this.run = run;
        }
    }

    public static Dialog confirm(Activity activity, Detail detail, String confirmLabel, Runnable confirm) {
        return confirm(activity, detail, new Action(confirmLabel, confirm));
    }

    /** Shows the detail card. Each action runs after the card closes; Cancel is added last. */
    public static Dialog confirm(Activity activity, Detail detail, Action... actions) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Context context = activity;
        int pad = FroglogTheme.dp(context, 18);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);

        FrameLayout top = new FrameLayout(context);
        if (detail.heroUrl != null) {
            ImageView hero = new ImageView(context);
            hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
            hero.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    FroglogTheme.dp(context, 150)));
            hero.setBackgroundColor(FroglogTheme.FIELD);
            hero.setAlpha(0.85f);
            FroglogImages.into(hero, detail.heroUrl, FroglogTheme.dp(context, 420));
            top.addView(hero);
        }
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(pad, detail.heroUrl != null ? FroglogTheme.dp(context, 96) : pad, pad, 0);
        ImageView art = cover(context, detail.coverUrl, 96, 128);
        art.setElevation(FroglogTheme.dp(context, 6));
        head.addView(art);
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = FroglogTheme.text(context, detail.title, 20, true);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(name);
        if (!detail.subtitle.isEmpty()) {
            titles.addView(FroglogTheme.muted(context, detail.subtitle, 13));
        }
        head.addView(titles);
        top.addView(head);
        body.addView(top);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, FroglogTheme.dp(context, 12), pad, 0);
        if (!detail.tags.isEmpty()) {
            LinearLayout tags = new LinearLayout(context);
            tags.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < detail.tags.size() && i < 3; i++) {
                TextView tag = FroglogTheme.chip(context, detail.tags.get(i), i == 0);
                tag.setMaxLines(1);
                tag.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.rightMargin = FroglogTheme.dp(context, 6);
                tag.setLayoutParams(params);
                tags.addView(tag);
            }
            content.addView(tags);
        }
        if (detail.description != null && !detail.description.isEmpty()) {
            TextView about = FroglogTheme.text(context, detail.description, 13, false);
            about.setMaxLines(6);
            about.setEllipsize(TextUtils.TruncateAt.END);
            about.setPadding(0, FroglogTheme.dp(context, 10), 0, 0);
            about.setLineSpacing(0, 1.15f);
            content.addView(about);
        }
        for (String[] row : detail.rows) {
            content.addView(field(context, row[0], row[1]));
        }
        if (detail.note != null && !detail.note.isEmpty()) {
            TextView note = FroglogTheme.muted(context, detail.note, 12);
            note.setPadding(0, FroglogTheme.dp(context, 10), 0, 0);
            content.addView(note);
        }
        body.addView(content);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(pad, FroglogTheme.dp(context, 16), pad, pad);
        View ok = null;
        for (int i = 0; i < actions.length; i++) {
            final Action action = actions[i];
            View.OnClickListener click = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    action.run.run();
                }
            };
            View button = i == 0 ? FroglogTheme.button(context, action.label, click)
                    : FroglogTheme.secondary(context, action.label, click);
            if (ok == null) {
                ok = button;
            }
            buttons.addView(button, wide(context, i == 0 ? 0 : 8));
        }
        buttons.addView(FroglogTheme.secondary(context, "Cancel", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        }), wide(context, actions.length == 0 ? 0 : 8));

        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        sheet.addView(buttons);
        sheet.setBackground(FroglogTheme.rounded(FroglogTheme.CARD, FroglogTheme.dp(context, 24)));
        sheet.setClipToOutline(true);
        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels
                    - FroglogTheme.dp(context, 32), FroglogTheme.dp(context, 460));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int code, KeyEvent event) {
                if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        dialog.dismiss();
                    }
                    return true;
                }
                if (code == KeyEvent.KEYCODE_BUTTON_A) {
                    View focus = dialog.getCurrentFocus();
                    if (focus != null && focus.isClickable()) {
                        if (event.getAction() == KeyEvent.ACTION_UP) {
                            focus.performClick();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        dialog.show();
        if (ok != null) {
            ok.requestFocus();
        }
        return dialog;
    }

    /** Per-game override of the pod's default session visibility, for {@code game:12} or {@code live:12}. */
    public static LinearLayout visibilityCard(Context context, String target) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(FroglogTheme.section(context, "Session visibility"));
        body.addView(FroglogTheme.muted(context, "For sessions Cocoon sends to this game. The pod setting stays the default for every other game.", 13));
        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setPadding(0, FroglogTheme.dp(context, 10), 0, 0);
        body.addView(choices);
        paintVisibility(context, choices, target);
        return FroglogTheme.card(context, body);
    }

    private static void paintVisibility(final Context context, final LinearLayout choices, final String target) {
        choices.removeAllViews();
        String current = FroglogStore.visibility(context, target);
        String[] values = {FroglogStore.VISIBILITY_DEFAULT, FroglogStore.VISIBILITY_PUBLIC, FroglogStore.VISIBILITY_PRIVATE};
        String[] labels = {"Default (" + (FroglogStore.sessionsPublic(context) ? "public" : "private") + ")",
                "Always public", "Always private"};
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            boolean on = value.equals(current);
            TextView chip = FroglogTheme.chip(context, labels[i], on);
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            int pad = FroglogTheme.dp(context, 8);
            chip.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                params.leftMargin = FroglogTheme.dp(context, 6);
            }
            chip.setLayoutParams(params);
            FroglogTheme.row(chip, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FroglogStore.setVisibility(context, target, value);
                    paintVisibility(context, choices, target);
                }
            });
            choices.addView(chip);
        }
    }

    private static LinearLayout.LayoutParams wide(Context context, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = FroglogTheme.dp(context, topDp);
        return params;
    }

    private static View field(Context context, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, FroglogTheme.dp(context, 7), 0, 0);
        TextView name = FroglogTheme.muted(context, label, 13);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(name);
        TextView shown = FroglogTheme.text(context, value, 13, true);
        shown.setGravity(Gravity.END);
        shown.setMaxLines(3);
        shown.setEllipsize(TextUtils.TruncateAt.END);
        shown.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));
        row.addView(shown);
        return row;
    }
}
