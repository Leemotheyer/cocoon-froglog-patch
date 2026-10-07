package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads Picnic screenshots to Froglog. Each Picnic record names its Cocoon game, and the
 * screenshot goes to the Froglog game that title is mapped to.
 */
public class FroglogPicnicActivity extends FroglogActivity {
    /** Opens on one screenshot from Picnic's upload button or the pod list. */
    public static final String EXTRA_URI = "froglog_shot_uri";
    public static final String EXTRA_TITLE = "froglog_shot_title";
    public static final String EXTRA_ALT_TITLE = "froglog_shot_alt_title";
    public static final String EXTRA_PLATFORM = "froglog_shot_platform";
    public static final String EXTRA_MIME = "froglog_shot_mime";
    public static final String EXTRA_NAME = "froglog_shot_name";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_SHOTS = 10;
    private static final int ROWS = 80;

    static final class Shot {
        String uri = "";
        String name = "";
        String mime;
        String title = "";
        String altTitle = "";
        String platform = "";
        long capturedAt;
        long size;
    }

    private final ExecutorService thumbs = Executors.newFixedThreadPool(2);
    private LinearLayout root;
    private boolean direct;
    private Shot shown;
    private List<FroglogGame> library;
    private boolean uploading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root);
        setContentView(scroll);
        FroglogTheme.paintSystemBars(this);
        String uri = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URI);
        if (uri != null && !uri.isEmpty()) {
            direct = true;
            Shot shot = find(this, uri);
            if (shot == null) {
                shot = shotFromExtras(getIntent());
            }
            if (shot == null) {
                shot = new Shot();
                shot.uri = uri;
                shot.name = Uri.parse(uri).getLastPathSegment();
            }
            showShot(shot);
        } else {
            showList();
        }
        loadLibrary();
    }

    private boolean resumed;

    /** A mapping made from here only shows after returning. */
    @Override
    protected void onResume() {
        super.onResume();
        if (resumed && shown != null && !uploading) {
            showShot(shown);
        }
        resumed = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thumbs.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (!direct && shown != null) {
            showList();
            return;
        }
        super.onBackPressed();
    }

    private void loadLibrary() {
        if (!FroglogStore.signedIn(this)) {
            return;
        }
        final String token = FroglogStore.token(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FroglogClient.Recent loaded = FroglogClient.library(token);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || loaded.games == null) {
                            return;
                        }
                        library = loaded.games;
                        if (shown != null && !uploading) {
                            showShot(shown);
                        }
                    }
                });
            }
        }, "froglog-picnic-library").start();
    }

    private void showList() {
        shown = null;
        root.removeAllViews();
        root.addView(FroglogTheme.title(this, "Picnic screenshots"));
        root.addView(FroglogTheme.muted(this,
                "Upload Picnic screenshots to the Froglog game each Cocoon title is mapped to. Each Froglog game holds up to 10.",
                14));
        if (!signInPrompt()) {
            return;
        }
        List<Shot> shots = recent(this);
        if (shots == null) {
            root.addView(gap(12));
            root.addView(FroglogTheme.muted(this, "Cocoon's Picnic library could not be read.", 14));
            return;
        }
        if (shots.isEmpty()) {
            root.addView(gap(12));
            root.addView(FroglogTheme.muted(this,
                    "No Picnic screenshots yet. Turn on Picnic in Cocoon and take one while a game is running.", 14));
            return;
        }
        root.addView(gap(8));
        String lastTitle = null;
        for (int i = 0; i < shots.size(); i++) {
            final Shot shot = shots.get(i);
            if (!shot.title.equals(lastTitle)) {
                root.addView(gap(14));
                root.addView(FroglogTheme.section(this, shot.title.isEmpty() ? "Unknown game" : shot.title));
                lastTitle = shot.title;
            }
            root.addView(shotRow(shot));
        }
    }

    private View shotRow(final Shot shot) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView thumb = thumbView(112, 63);
        ((LinearLayout.LayoutParams) thumb.getLayoutParams()).rightMargin = dp(12);
        row.addView(thumb);
        loadThumb(thumb, shot.uri, dp(160));
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView when = FroglogTheme.text(this, captured(shot), 14, true);
        lines.addView(when);
        TextView state = FroglogTheme.muted(this, stateLine(shot), 12);
        state.setMaxLines(2);
        state.setEllipsize(TextUtils.TruncateAt.END);
        lines.addView(state);
        row.addView(lines);
        if (FroglogStore.shotUploaded(this, shot.uri)) {
            row.addView(FroglogTheme.chip(this, "Uploaded", true));
        }
        LinearLayout card = FroglogTheme.card(this, row);
        card.setPadding(dp(10), dp(10), dp(12), dp(10));
        ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = dp(8);
        FroglogTheme.row(card, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showShot(shot);
            }
        });
        return card;
    }

    private String stateLine(Shot shot) {
        String target = target(shot);
        if (target != null) {
            FroglogGame game = libraryGame(target);
            return "Uploads to " + (game != null ? game.title : "its mapped Froglog game");
        }
        if (declined(shot)) {
            return "Not sent to Froglog for this game";
        }
        return "Not mapped to a Froglog game yet";
    }

    private void showShot(final Shot shot) {
        shown = shot;
        root.removeAllViews();
        root.addView(FroglogTheme.section(this, "Picnic screenshot"));
        root.addView(FroglogTheme.title(this, shot.title.isEmpty() ? "Screenshot" : shot.title));
        String when = captured(shot);
        if (!when.isEmpty()) {
            root.addView(FroglogTheme.muted(this, when, 13));
        }
        root.addView(gap(12));
        ImageView preview = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        preview.setLayoutParams(params);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(FroglogTheme.rounded(FroglogTheme.FIELD, dp(16)));
        preview.setClipToOutline(true);
        root.addView(preview);
        if (!shot.title.isEmpty()) {
            loadThumb(preview, shot.uri, Math.max(getResources().getDisplayMetrics().widthPixels, dp(360)));
        }

        if (!signInPrompt()) {
            return;
        }
        if (shot.title.isEmpty()) {
            root.addView(gap(14));
            root.addView(FroglogTheme.muted(this,
                    "Picnic has no game for this screenshot, so it cannot be matched to a Froglog game.",
                    14));
            return;
        }
        final String target = target(shot);
        root.addView(gap(16));
        root.addView(FroglogTheme.section(this, "Froglog game"));
        if (target == null) {
            root.addView(FroglogTheme.muted(this, declined(shot)
                    ? "Sessions for this game are not sent to Froglog. Map it to a Froglog game to upload screenshots."
                    : "This game is not mapped to a Froglog game yet. Map it first, then come back to upload.", 14));
            root.addView(gap(10));
            root.addView(wide(FroglogTheme.button(this, "Map this game", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openMapping(shot);
                }
            })));
            return;
        }
        final long gameId = Long.parseLong(target.substring(5));
        FroglogGame game = libraryGame(target);
        if (game != null) {
            root.addView(FroglogCards.gameRow(this, game.title, game.meta, game.coverUrl, null));
        } else {
            root.addView(FroglogTheme.muted(this, target.startsWith("live:")
                    ? "Mapped to a live service game on Froglog."
                    : "Mapped to a Froglog game.", 14));
        }
        final TextView count = FroglogTheme.muted(this, "Checking how many screenshots the game has…", 13);
        root.addView(gap(6));
        root.addView(count);
        final String token = FroglogStore.token(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int used = FroglogClient.screenshotCount(token, gameId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (shown != shot) {
                            return;
                        }
                        if (used < 0) {
                            count.setText("Froglog takes up to 10 screenshots per game.");
                        } else if (used >= MAX_SHOTS) {
                            count.setText("This game already has 10 screenshots on Froglog. Remove one there first.");
                            count.setTextColor(FroglogTheme.WARN);
                        } else {
                            count.setText(used + " of 10 screenshots used on this game.");
                        }
                    }
                });
            }
        }, "froglog-shot-count").start();

        root.addView(gap(14));
        final EditText caption = FroglogTheme.field(this, "Caption (optional)");
        root.addView(caption);
        root.addView(gap(10));
        final boolean[] spoiler = {false};
        final TextView spoilerChip = FroglogTheme.text(this, spoilerLabel(false), 14, false);
        spoilerChip.setPadding(dp(14), dp(10), dp(14), dp(10));
        spoilerChip.setBackground(FroglogTheme.rounded(FroglogTheme.FIELD, dp(14)));
        FroglogTheme.row(spoilerChip, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spoiler[0] = !spoiler[0];
                spoilerChip.setText(spoilerLabel(spoiler[0]));
            }
        });
        root.addView(spoilerChip);
        root.addView(gap(14));
        final TextView status = FroglogTheme.muted(this,
                FroglogStore.shotUploaded(this, shot.uri) ? "Already uploaded once from this device." : "", 13);
        final Button upload = FroglogTheme.button(this,
                FroglogStore.shotUploaded(this, shot.uri) ? "Upload again" : "Upload to Froglog", null);
        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                upload(shot, gameId, caption.getText().toString(), spoiler[0], upload, status);
            }
        });
        root.addView(wide(upload));
        root.addView(gap(8));
        root.addView(status);
        root.addView(gap(8));
        root.addView(wide(FroglogTheme.secondary(this, "Change mapping", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMapping(shot);
            }
        })));
        upload.requestFocus();
    }

    private static String spoilerLabel(boolean on) {
        return on ? "Spoiler: yes" : "Spoiler: no";
    }

    private void upload(final Shot shot, final long gameId, final String caption, final boolean spoiler,
            final Button button, final TextView status) {
        if (uploading) {
            return;
        }
        uploading = true;
        button.setEnabled(false);
        status.setTextColor(FroglogTheme.MUTED);
        status.setText("Uploading…");
        final String token = FroglogStore.token(this);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    int used = FroglogClient.screenshotCount(token, gameId);
                    if (used >= MAX_SHOTS) {
                        throw new IllegalStateException("This game already has 10 screenshots on Froglog");
                    }
                    Payload payload = payload(app, shot);
                    FroglogClient.uploadScreenshot(token, gameId, payload.bytes, payload.mime, payload.name,
                            caption, spoiler);
                    FroglogStore.markShotUploaded(app, shot.uri);
                } catch (Exception e) {
                    error = e.getMessage() == null ? "Upload failed" : e.getMessage();
                }
                final String failed = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        uploading = false;
                        if (isFinishing()) {
                            return;
                        }
                        button.setEnabled(true);
                        if (failed != null) {
                            status.setTextColor(FroglogTheme.WARN);
                            status.setText(failed);
                            return;
                        }
                        Toast.makeText(FroglogPicnicActivity.this, "Uploaded to Froglog", Toast.LENGTH_SHORT).show();
                        if (direct) {
                            finish();
                        } else {
                            showList();
                        }
                    }
                });
            }
        }, "froglog-shot-upload").start();
    }

    static final class Payload {
        byte[] bytes;
        String mime;
        String name;
    }

    /** The original file when Froglog takes it as is, otherwise a JPEG under 10 MB. */
    static Payload payload(Context context, Shot shot) throws Exception {
        Uri uri = Uri.parse(shot.uri);
        String mime = shot.mime;
        if (mime == null || mime.isEmpty()) {
            mime = context.getContentResolver().getType(uri);
        }
        byte[] raw = readAll(context, uri, MAX_BYTES + 1);
        Payload out = new Payload();
        String base = shot.name == null || shot.name.isEmpty() ? "screenshot" : shot.name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        boolean accepted = mime != null && (mime.equals("image/jpeg") || mime.equals("image/png")
                || mime.equals("image/gif") || mime.equals("image/webp"));
        if (raw != null && raw.length <= MAX_BYTES && accepted) {
            out.bytes = raw;
            out.mime = mime;
            out.name = base + "." + mime.substring(6).replace("jpeg", "jpg");
            return out;
        }
        Bitmap bitmap = decode(context, uri, 2560);
        if (bitmap == null) {
            throw new IllegalStateException("The screenshot could not be read");
        }
        int quality = 90;
        byte[] jpeg;
        do {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer);
            jpeg = buffer.toByteArray();
            quality -= 15;
        } while (jpeg.length > MAX_BYTES && quality > 30);
        bitmap.recycle();
        if (jpeg.length > MAX_BYTES) {
            throw new IllegalStateException("That screenshot is over Froglog's 10 MB limit");
        }
        out.bytes = jpeg;
        out.mime = "image/jpeg";
        out.name = base + ".jpg";
        return out;
    }

    /** Null when the stream is longer than {@code limit}. */
    private static byte[] readAll(Context context, Uri uri, int limit) throws Exception {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IllegalStateException("The screenshot could not be opened");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = in.read(chunk)) > 0) {
                out.write(chunk, 0, read);
                if (out.size() > limit) {
                    return null;
                }
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    static Bitmap decode(Context context, Uri uri, int maxPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            try {
                BitmapFactory.decodeStream(in, null, bounds);
            } finally {
                in.close();
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            try {
                return BitmapFactory.decodeStream(in, null, options);
            } finally {
                in.close();
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void loadThumb(final ImageView view, final String uri, final int maxPx) {
        view.setTag(uri);
        final Context app = getApplicationContext();
        try {
            thumbs.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = decode(app, Uri.parse(uri), maxPx);
                    if (bitmap == null) {
                        return;
                    }
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            if (uri.equals(view.getTag())) {
                                view.setImageBitmap(bitmap);
                            }
                        }
                    });
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private ImageView thumbView(int widthDp, int heightDp) {
        ImageView view = new ImageView(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackground(FroglogTheme.rounded(FroglogTheme.FIELD, dp(10)));
        view.setClipToOutline(true);
        return view;
    }

    private boolean signInPrompt() {
        if (FroglogStore.signedIn(this)) {
            return true;
        }
        root.addView(gap(14));
        root.addView(FroglogTheme.muted(this, "Sign in to Froglog in the Froglog pod to upload screenshots.", 14));
        root.addView(gap(10));
        root.addView(wide(FroglogTheme.button(this, "Open Froglog", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FroglogPicnicActivity.this, FroglogPodActivity.class));
            }
        })));
        return false;
    }

    private void openMapping(Shot shot) {
        startActivity(new Intent(this, FroglogMappingsActivity.class)
                .putExtra(FroglogMappingsActivity.EXTRA_TITLE, shot.title)
                .putExtra(FroglogMappingsActivity.EXTRA_PLATFORM, shot.platform));
    }

    /** Tries the title and then the display name, the two names a session can carry. */
    private String target(Shot shot) {
        String target = FroglogStore.mappedTarget(this, shot.title, shot.platform);
        if (target == null && !shot.altTitle.isEmpty()) {
            target = FroglogStore.mappedTarget(this, shot.altTitle, shot.platform);
        }
        return target;
    }

    private boolean declined(Shot shot) {
        return FroglogStore.declined(this, shot.title, shot.platform)
                || (!shot.altTitle.isEmpty() && FroglogStore.declined(this, shot.altTitle, shot.platform));
    }

    private FroglogGame libraryGame(String target) {
        if (library == null || target == null) {
            return null;
        }
        boolean live = target.startsWith("live:");
        long id = Long.parseLong(target.substring(5));
        for (int i = 0; i < library.size(); i++) {
            FroglogGame game = library.get(i);
            if (game != null && game.id == id && game.live == live) {
                return game;
            }
        }
        return null;
    }

    private static String captured(Shot shot) {
        if (shot.capturedAt <= 0) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(shot.capturedAt));
    }

    private static final String SELECT =
            "SELECT r.screenshotUri, r.displayName, r.mimeType, r.gameName, r.platformId, r.capturedAt, r.sizeBytes, "
                    + "g.title, g.displayName FROM picnic_screenshot_records r LEFT JOIN games g ON g.id = r.gameId ";

    /** Newest first, grouped by game so the list reads one game at a time. Null when the database cannot be read. */
    static List<Shot> recent(Context context) {
        List<Shot> shots = query(context, SELECT + "ORDER BY r.capturedAt DESC LIMIT " + ROWS, null);
        if (shots == null) {
            return null;
        }
        List<Shot> grouped = new ArrayList<Shot>();
        List<String> order = new ArrayList<String>();
        for (int i = 0; i < shots.size(); i++) {
            if (!order.contains(shots.get(i).title)) {
                order.add(shots.get(i).title);
            }
        }
        for (int t = 0; t < order.size(); t++) {
            for (int i = 0; i < shots.size(); i++) {
                if (shots.get(i).title.equals(order.get(t))) {
                    grouped.add(shots.get(i));
                }
            }
        }
        return grouped;
    }

    static Shot find(Context context, String uri) {
        List<Shot> shots = query(context, SELECT + "WHERE r.screenshotUri = ? LIMIT 1", new String[] {uri});
        return shots == null || shots.isEmpty() ? null : shots.get(0);
    }

    private static Shot shotFromExtras(Intent intent) {
        if (intent == null) {
            return null;
        }
        String uri = intent.getStringExtra(EXTRA_URI);
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        Shot shot = new Shot();
        shot.uri = uri;
        shot.name = intent.getStringExtra(EXTRA_NAME);
        if (shot.name == null || shot.name.isEmpty()) {
            shot.name = Uri.parse(uri).getLastPathSegment();
        }
        shot.mime = intent.getStringExtra(EXTRA_MIME);
        shot.title = nonNull(intent.getStringExtra(EXTRA_TITLE)).trim();
        shot.altTitle = nonNull(intent.getStringExtra(EXTRA_ALT_TITLE)).trim();
        shot.platform = nonNull(intent.getStringExtra(EXTRA_PLATFORM));
        return shot;
    }

    private static List<Shot> query(Context context, String sql, String[] args) {
        List<Shot> shots = new ArrayList<Shot>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            String path = context.getDatabasePath("cocoon_db").getPath();
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(sql, args);
            while (cursor.moveToNext()) {
                Shot shot = new Shot();
                shot.uri = nonNull(cursor.getString(0));
                shot.name = nonNull(cursor.getString(1));
                shot.mime = cursor.getString(2);
                String recordName = nonNull(cursor.getString(3)).trim();
                shot.platform = nonNull(cursor.getString(4));
                shot.capturedAt = cursor.getLong(5);
                shot.size = cursor.getLong(6);
                String title = nonNull(cursor.getString(7)).trim();
                String display = nonNull(cursor.getString(8)).trim();
                shot.title = !title.isEmpty() ? title : !display.isEmpty() ? display : recordName;
                shot.altTitle = !display.equals(shot.title) ? display
                        : !recordName.equals(shot.title) ? recordName : "";
                if (!shot.uri.isEmpty()) {
                    shots.add(shot);
                }
            }
        } catch (RuntimeException e) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return shots;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private View gap(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private View wide(View view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private int dp(int value) {
        return FroglogTheme.dp(this, value);
    }
}
