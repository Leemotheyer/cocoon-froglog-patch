package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Sign-in screen for the Froglog widget. Does not change Cocoon's own accounts. */
public class FroglogWidgetConfig extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private TextView status;
    private EditText username;
    private EditText password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent() == null ? null : getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }
        setResult(RESULT_CANCELED);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.parseColor("#121418"));

        TextView title = text("Froglog", 22, true);
        TextView body = text("A separate widget for your recent public Froglog games. Cocoon's Recently played tile is unchanged.", 14, false);
        body.setTextColor(Color.parseColor("#C8C2B8"));

        username = field("Username");
        password = field("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        status = text(signedInLabel(), 13, false);
        status.setTextColor(Color.parseColor("#A8A29A"));

        Button save = button("Sign in");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn();
            }
        });
        Button signOut = button("Sign out");
        signOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FroglogStore.clear(FroglogWidgetConfig.this);
                status.setText("Signed out");
                refreshWidgets();
            }
        });

        root.addView(title);
        root.addView(gap());
        root.addView(body);
        root.addView(gap());
        root.addView(username);
        root.addView(gap());
        root.addView(password);
        root.addView(gap());
        root.addView(save);
        root.addView(gap());
        root.addView(signOut);
        root.addView(gap());
        root.addView(status);
        if (FroglogStore.signedIn(this)) {
            username.setText(FroglogStore.username(this));
        }
        setContentView(root);
    }

    private void signIn() {
        final String name = username.getText().toString().trim();
        final String pass = password.getText().toString();
        if (name.isEmpty() || pass.isEmpty()) {
            status.setText("Enter your Froglog username and password");
            return;
        }
        status.setText("Signing in…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final FroglogClient.Session session = FroglogClient.login(name, pass);
                    FroglogStore.save(FroglogWidgetConfig.this, session.token, session.username);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("Signed in as " + session.username);
                            refreshWidgets();
                            finishWithOk();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText(e.getMessage() == null ? "Could not sign in" : e.getMessage());
                        }
                    });
                }
            }
        }, "froglog-login").start();
    }

    private void refreshWidgets() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new ComponentName(this, FroglogRecentWidget.class));
        if (ids.length > 0) {
            new FroglogRecentWidget().onUpdate(this, manager, ids);
        }
    }

    private void finishWithOk() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            android.content.Intent result = new android.content.Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
        }
        finish();
    }

    private String signedInLabel() {
        if (FroglogStore.signedIn(this)) {
            return "Signed in as " + FroglogStore.username(this);
        }
        return "Not signed in. Games must be public on Froglog to appear here.";
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor("#F4F1EA"));
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private EditText field(String hint) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setHintTextColor(Color.parseColor("#8A847C"));
        view.setTextColor(Color.parseColor("#F4F1EA"));
        view.setSingleLine(true);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1C2028"));
        bg.setCornerRadius(dp(10));
        view.setBackground(bg);
        return view;
    }

    private Button button(String label) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextColor(Color.parseColor("#121418"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#8BD17C"));
        bg.setCornerRadius(dp(12));
        view.setBackground(bg);
        return view;
    }

    private View gap() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
