package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.addView(FroglogTheme.title(this, "Add a Cocoon game"));
        TextView copy = FroglogTheme.text(this, "Choose a recent game from your Cocoon library.", 14, false);
        copy.setTextColor(FroglogTheme.MUTED);
        root.addView(copy);
        List<CocoonLibrary.Game> games = CocoonLibrary.recent(this);
        if (games.isEmpty()) {
            TextView empty = FroglogTheme.text(this, "Cocoon's library has no recent games to add yet.", 14, false);
            empty.setTextColor(FroglogTheme.MUTED);
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.addView(empty);
            root.addView(FroglogTheme.card(this, wrap));
        }
        for (int i = 0; i < games.size(); i++) {
            final CocoonLibrary.Game game = games.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(FroglogTheme.text(this, game.title, 16, true));
            if (game.platformName != null && !game.platformName.isEmpty()) {
                TextView meta = FroglogTheme.text(this, game.platformName, 13, false);
                meta.setTextColor(FroglogTheme.MUTED);
                row.addView(meta);
            }
            View card = FroglogTheme.card(this, row);
            card.setOnClickListener(new View.OnClickListener() {
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
            root.addView(card);
        }
        ScrollView scroll = new ScrollView(this);
        FroglogTheme.page(scroll);
        scroll.addView(root);
        setContentView(scroll);
        FroglogTheme.paintSystemBars(this);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
