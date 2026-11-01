package rip.moth.cocoonshell.froglog;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** A small cover beside the title, then every field Froglog has for the game, its review, and sessions. */
public class FroglogGameDetail extends FroglogActivity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_META = "meta";
    public static final String EXTRA_REVIEW = "review";
    public static final String EXTRA_COVER = "cover";
    public static final String EXTRA_JSON = "json";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_LIVE = "live";
    /** True when the game is in the signed-in user's library, so its sessions can be read. */
    public static final String EXTRA_OWN = "own";

    private static final int SESSIONS_SHOWN = 20;

    public static Intent intent(Context context, FroglogGame game, boolean own) {
        Intent detail = new Intent(context, FroglogGameDetail.class);
        detail.putExtra(EXTRA_TITLE, game.title);
        detail.putExtra(EXTRA_META, game.meta);
        detail.putExtra(EXTRA_REVIEW, game.review);
        detail.putExtra(EXTRA_COVER, game.coverUrl == null ? "" : game.coverUrl);
        detail.putExtra(EXTRA_JSON, game.json == null ? "" : game.json);
        detail.putExtra(EXTRA_ID, game.id);
        detail.putExtra(EXTRA_LIVE, game.live);
        detail.putExtra(EXTRA_OWN, own);
        return detail;
    }

    private LinearLayout sessionList;
    private LinearLayout detailsHost;
    private LinearLayout tagRow;
    private JSONObject gameJson;
    private boolean liveGame;
    private boolean ownGame;
    private boolean editing;
    private long gameId;
    private View firstEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = extra(EXTRA_TITLE, "Froglog");
        String meta = extra(EXTRA_META, "");
        String review = extra(EXTRA_REVIEW, "");
        final String cover = extra(EXTRA_COVER, "");
        JSONObject game = parse(extra(EXTRA_JSON, ""));
        Intent intent = getIntent();
        final long id = intent == null ? -1 : intent.getLongExtra(EXTRA_ID, -1);
        final boolean live = intent != null && intent.getBooleanExtra(EXTRA_LIVE, false);
        boolean own = intent != null && intent.getBooleanExtra(EXTRA_OWN, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        gameJson = game;
        liveGame = live;
        ownGame = own && id > 0;
        gameId = id;
        List<FroglogGameInfo.Line> rows = FroglogGameInfo.lines(game);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        ImageView art = FroglogCards.cover(this, cover.isEmpty() ? null : cover, 96, 128);
        ((LinearLayout.LayoutParams) art.getLayoutParams()).rightMargin = dp(16);
        header.addView(art);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = FroglogTheme.title(this, title);
        name.setTextSize(22);
        heading.addView(name);
        TextView line = FroglogTheme.text(this, meta.isEmpty() ? "No session yet" : meta, 14, false);
        line.setTextColor(FroglogTheme.MUTED);
        line.setPadding(0, dp(2), 0, 0);
        heading.addView(line);
        tagRow = tags(rows, live);
        heading.addView(tagRow);
        header.addView(heading);
        root.addView(FroglogTheme.card(this, header));

        String about = FroglogGameInfo.description(game);
        if (about != null) {
            LinearLayout aboutBody = column();
            aboutBody.addView(FroglogTheme.section(this, "About"));
            final TextView aboutText = FroglogTheme.text(this, about, 14, false);
            aboutText.setLineSpacing(0, 1.15f);
            aboutText.setMaxLines(6);
            aboutText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            aboutBody.addView(aboutText);
            if (about.length() > 280) {
                final TextView more = FroglogTheme.text(this, "Show more", 13, true);
                more.setTextColor(FroglogTheme.ACCENT);
                more.setPadding(0, dp(6), 0, 0);
                FroglogTheme.row(more, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean open = aboutText.getMaxLines() != Integer.MAX_VALUE;
                        aboutText.setMaxLines(open ? Integer.MAX_VALUE : 6);
                        more.setText(open ? "Show less" : "Show more");
                    }
                });
                aboutBody.addView(more);
            }
            root.addView(spaced(FroglogTheme.card(this, aboutBody)));
        }

        detailsHost = column();
        root.addView(detailsHost);
        renderDetails(false);

        if (!review.isEmpty()) {
            LinearLayout reviewBody = column();
            reviewBody.addView(FroglogTheme.section(this, "Review"));
            TextView reviewText = FroglogTheme.text(this, review, 15, false);
            reviewText.setLineSpacing(0, 1.15f);
            reviewBody.addView(reviewText);
            root.addView(spaced(FroglogTheme.card(this, reviewBody)));
        }

        if (own && id > 0 && FroglogStore.signedIn(this)) {
            root.addView(spaced(FroglogCards.visibilityCard(this, FroglogLinks.value(id, live))));
        }

        final String token = own && id > 0 ? FroglogStore.token(this) : null;
        if (token != null && !token.isEmpty()) {
            sessionList = column();
            sessionList.addView(FroglogTheme.section(this, "Sessions"));
            sessionList.addView(muted("Loading sessions…"));
            root.addView(spaced(FroglogTheme.card(this, sessionList)));
            loadSessions(token, live, id);
        }

        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        scroll.addView(root);
        setContentView(scroll);
        FroglogTheme.paintSystemBars(this);
    }

    @Override
    public void onBackPressed() {
        if (editing) {
            renderDetails(false);
            return;
        }
        super.onBackPressed();
    }

    /** Status, platform, and rating as chips under the title. They also stay in Details. */
    private LinearLayout tags(List<FroglogGameInfo.Line> rows, boolean live) {
        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setPadding(0, dp(8), 0, 0);
        if (live) {
            addTag(tags, "Live service", true);
        }
        for (int i = 0; i < rows.size() && tags.getChildCount() < 3; i++) {
            String label = rows.get(i).label;
            String value = rows.get(i).value;
            if ("Status".equals(label)) {
                addTag(tags, value, !live);
            } else if ("Platform".equals(label)) {
                addTag(tags, value, false);
            } else if ("Rating".equals(label)) {
                addTag(tags, value.startsWith("★") ? value : "★ " + value, false);
            }
        }
        return tags;
    }

    private void addTag(LinearLayout tags, String value, boolean strong) {
        TextView chip = FroglogTheme.chip(this, value, strong);
        chip.setMaxLines(1);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (tags.getChildCount() > 0) {
            params.leftMargin = dp(6);
        }
        chip.setLayoutParams(params);
        tags.addView(chip);
    }

    private void renderDetails(boolean edit) {
        editing = edit;
        firstEditor = null;
        detailsHost.removeAllViews();
        List<FroglogGameInfo.Line> lines = FroglogGameInfo.lines(gameJson);
        refreshTags(lines);
        if (lines.isEmpty()) {
            return;
        }
        LinearLayout details = column();
        details.addView(FroglogTheme.section(this, "Details"));
        final boolean canEdit = ownGame && FroglogStore.signedIn(this) && gameJson != null;
        if (canEdit && !edit) {
            TextView hint = muted("Press A to edit");
            hint.setPadding(0, 0, 0, dp(4));
            details.addView(hint);
        }
        final ArrayList<Editor> editors = new ArrayList<Editor>();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                details.addView(divider());
            }
            FroglogGameInfo.Line line = lines.get(i);
            if (!edit || "readonly".equals(line.editor)) {
                details.addView(field(line.label, line.value));
                continue;
            }
            Editor editor = editor(line);
            editors.add(editor);
            details.addView(editor.row);
            if (firstEditor == null) {
                firstEditor = editor.focus;
            }
        }
        if (canEdit && edit) {
            TextView error = muted("");
            error.setPadding(0, dp(8), 0, 0);
            details.addView(error);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(10), 0, 0);
            Button save = FroglogTheme.button(this, "Save", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveEdits(editors, error);
                }
            });
            Button cancel = FroglogTheme.secondary(this, "Cancel", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    renderDetails(false);
                }
            });
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gap.leftMargin = dp(8);
            cancel.setLayoutParams(gap);
            actions.addView(save);
            actions.addView(cancel);
            details.addView(actions);
        }
        LinearLayout card = FroglogTheme.card(this, details);
        if (canEdit && !edit) {
            card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            FroglogTheme.row(card, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    renderDetails(true);
                }
            });
        } else {
            card.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        detailsHost.addView(spaced(card));
        if (edit && firstEditor != null) {
            final View focus = firstEditor;
            focus.post(new Runnable() {
                @Override
                public void run() {
                    focus.requestFocus();
                }
            });
        }
    }

    private void refreshTags(List<FroglogGameInfo.Line> lines) {
        if (tagRow == null) {
            return;
        }
        tagRow.removeAllViews();
        if (liveGame) {
            addTag(tagRow, "Live service", true);
        }
        for (int i = 0; i < lines.size() && tagRow.getChildCount() < 3; i++) {
            String label = lines.get(i).label;
            String value = lines.get(i).value;
            if ("Status".equals(label)) {
                addTag(tagRow, value, !liveGame);
            } else if ("Platform".equals(label)) {
                addTag(tagRow, value, false);
            } else if ("Rating".equals(label)) {
                addTag(tagRow, value.startsWith("★") ? value : "★ " + value, false);
            }
        }
    }

    private Editor editor(final FroglogGameInfo.Line line) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView name = FroglogTheme.text(this, line.label, 13, false);
        name.setTextColor(FroglogTheme.MUTED);
        row.addView(name);
        View focus;
        final Value value;
        if ("status".equals(line.editor) || "bool".equals(line.editor)) {
            final Spinner spinner = new Spinner(this);
            String[] options = FroglogGameInfo.choices(line.editor, liveGame);
            ArrayList<String> choiceList = new ArrayList<String>();
            for (int c = 0; c < options.length; c++) {
                choiceList.add(options[c]);
            }
            if (!line.value.isEmpty()) {
                boolean known = false;
                for (int c = 0; c < choiceList.size(); c++) {
                    if (choiceList.get(c).equalsIgnoreCase(line.value)) {
                        known = true;
                    }
                }
                if (!known) {
                    choiceList.add(line.value);
                }
            }
            final String[] choices = choiceList.toArray(new String[choiceList.size()]);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, choices);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            int selected = 0;
            for (int i = 0; i < choices.length; i++) {
                if (choices[i].equalsIgnoreCase(line.value)) {
                    selected = i;
                }
            }
            spinner.setSelection(selected);
            spinner.setFocusable(true);
            spinner.setClickable(true);
            row.addView(spinner);
            focus = spinner;
            value = new Value() {
                @Override
                public String get() {
                    Object item = spinner.getSelectedItem();
                    return item == null ? "" : String.valueOf(item);
                }
            };
        } else if ("date".equals(line.editor)) {
            final Button pick = FroglogTheme.secondary(this, line.value.isEmpty() ? "Pick a date" : line.value, null);
            pick.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickDate(pick);
                }
            });
            final Button clear = FroglogTheme.secondary(this, "Clear", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.setText("Pick a date");
                    pick.setTag("");
                }
            });
            pick.setTag(line.value);
            LinearLayout dates = new LinearLayout(this);
            dates.setOrientation(LinearLayout.HORIZONTAL);
            dates.addView(pick);
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gap.leftMargin = dp(8);
            clear.setLayoutParams(gap);
            dates.addView(clear);
            row.addView(dates);
            focus = pick;
            value = new Value() {
                @Override
                public String get() {
                    Object tag = pick.getTag();
                    if (tag == null || "Pick a date".equals(String.valueOf(pick.getText()))) {
                        return "";
                    }
                    return String.valueOf(tag);
                }
            };
        } else {
            final EditText input = FroglogTheme.field(this, line.label);
            String shown = line.value;
            if ("number".equals(line.editor)) {
                shown = shown.replace("★", "").replace("h", "").trim();
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }
            input.setText(shown);
            row.addView(input);
            focus = input;
            value = new Value() {
                @Override
                public String get() {
                    return input.getText() == null ? "" : input.getText().toString();
                }
            };
        }
        return new Editor(line, row, focus, value);
    }

    private void pickDate(final Button pick) {
        Calendar calendar = Calendar.getInstance();
        Object tag = pick.getTag();
        String current = tag == null ? "" : String.valueOf(tag);
        if (current.length() >= 10 && current.charAt(4) == '-') {
            try {
                calendar.set(Calendar.YEAR, Integer.parseInt(current.substring(0, 4)));
                calendar.set(Calendar.MONTH, Integer.parseInt(current.substring(5, 7)) - 1);
                calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(current.substring(8, 10)));
            } catch (NumberFormatException ignored) {
                // The picker opens on today when the stored date cannot be read.
            }
        }
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(android.widget.DatePicker view, int year, int month, int day) {
                String date = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                pick.setTag(date);
                pick.setText(date);
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveEdits(final List<Editor> editors, final TextView error) {
        final JSONObject next;
        try {
            next = new JSONObject(gameJson.toString());
        } catch (Exception e) {
            error.setText("Could not read this game");
            return;
        }
        for (int i = 0; i < editors.size(); i++) {
            Editor editor = editors.get(i);
            String problem = FroglogGameInfo.apply(next, liveGame, editor.line.key, editor.line.label,
                    editor.line.editor, editor.value.get());
            if (problem != null) {
                error.setText(problem);
                editor.focus.requestFocus();
                return;
            }
        }
        error.setText("Saving…");
        final String token = FroglogStore.token(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String failure = null;
                try {
                    FroglogClient.updateGame(token, gameId, liveGame, next);
                } catch (Exception e) {
                    failure = e.getMessage() == null ? "Could not reach Froglog" : e.getMessage();
                }
                final String message = failure;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        if (message != null) {
                            error.setText(message);
                            return;
                        }
                        gameJson = next;
                        renderDetails(false);
                    }
                });
            }
        }, "froglog-game-edit").start();
    }

    private View divider() {
        View line = new View(this);
        line.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        line.setBackgroundColor(FroglogTheme.LINE);
        return line;
    }

    private void loadSessions(final String token, final boolean live, final long id) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String[]> found = null;
                String error = null;
                try {
                    found = FroglogGameInfo.sessions(FroglogClient.gameSessions(token, live, id));
                } catch (Exception e) {
                    error = e.getMessage() == null ? "Could not load the sessions" : e.getMessage();
                }
                final List<String[]> sessions = found;
                final String failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            showSessions(sessions, failure);
                        }
                    }
                });
            }
        }, "froglog-detail-sessions").start();
    }

    private void showSessions(List<String[]> sessions, String error) {
        while (sessionList.getChildCount() > 1) {
            sessionList.removeViewAt(1);
        }
        if (error != null) {
            sessionList.addView(muted(error));
            return;
        }
        if (sessions.isEmpty()) {
            sessionList.addView(muted("No sessions logged yet."));
            return;
        }
        TextView count = muted(sessions.size() == 1 ? "1 session" : sessions.size() + " sessions");
        count.setPadding(0, 0, 0, dp(4));
        sessionList.addView(count);
        int shown = Math.min(sessions.size(), SESSIONS_SHOWN);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sessionList.addView(divider());
            }
            String[] session = sessions.get(i);
            String when = session[0].isEmpty() ? "Undated" : session[0];
            sessionList.addView(field(when, session[1].isEmpty() ? "—" : session[1]));
            if (!session[2].isEmpty()) {
                TextView notes = muted(session[2]);
                notes.setPadding(0, 0, 0, dp(6));
                sessionList.addView(notes);
            }
        }
        if (sessions.size() > shown) {
            sessionList.addView(muted("+" + (sessions.size() - shown) + " older sessions"));
        }
    }

    private View field(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView name = FroglogTheme.text(this, label, 14, false);
        name.setTextColor(FroglogTheme.MUTED);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(name);
        TextView shown = FroglogTheme.text(this, value, 14, true);
        shown.setGravity(Gravity.END);
        shown.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));
        row.addView(shown);
        return row;
    }

    private TextView muted(String value) {
        TextView text = FroglogTheme.text(this, value, 13, false);
        text.setTextColor(FroglogTheme.MUTED);
        return text;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private View spaced(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        card.setLayoutParams(params);
        return card;
    }

    private static JSONObject parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extra(String key, String fallback) {
        String value = getIntent() == null ? null : getIntent().getStringExtra(key);
        return value == null ? fallback : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface Value {
        String get();
    }

    private static final class Editor {
        final FroglogGameInfo.Line line;
        final View row;
        final View focus;
        final Value value;

        Editor(FroglogGameInfo.Line line, View row, View focus, Value value) {
            this.line = line;
            this.row = row;
            this.focus = focus;
            this.value = value;
        }
    }
}
