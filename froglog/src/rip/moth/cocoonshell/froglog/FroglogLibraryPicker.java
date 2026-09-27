package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Picks a game from Cocoon's library and opens the Froglog add screen. */
public class FroglogLibraryPicker extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        TextView title = new TextView(this);
        title.setText("Add a Cocoon game");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#F4F1EA"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);
        List<CocoonLibrary.Game> games = CocoonLibrary.recent(this);
        if (games.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Cocoon's library has no recent games to add yet.");
            empty.setTextColor(Color.parseColor("#C8C2B8"));
            empty.setPadding(0, dp(16), 0, 0);
            root.addView(empty);
        }
        for (int i = 0; i < games.size(); i++) {
            final CocoonLibrary.Game game = games.get(i);
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(game.title + (game.platformName == null ? "" : " · " + game.platformName));
            button.setTextColor(Color.parseColor("#121418"));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.parseColor("#8BD17C"));
            bg.setCornerRadius(dp(12));
            button.setBackground(bg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(12);
            button.setLayoutParams(params);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(FroglogLibraryPicker.this, FroglogAddGame.class);
                    String platformId = game.platformId == null ? "" : game.platformId;
                    String platformName = game.platformName == null ? platformId : game.platformName;
                    intent.putExtra(FroglogAddGame.EXTRA_TITLE, game.title);
                    intent.putExtra(FroglogAddGame.EXTRA_PLATFORM, platformId.isEmpty() ? platformName : platformId);
                    intent.putExtra(FroglogAddGame.EXTRA_PLATFORM_LABEL, platformName);
                    startActivity(intent);
                }
            });
            root.addView(button);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#121418"));
        scroll.addView(root);
        setContentView(scroll);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
