package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Older prompts open the mapping screen. The queue itself lives in the Froglog pod. */
public class FroglogSessionPrompt extends Activity {
    public static final String EXTRA_TITLE = FroglogMapActivity.EXTRA_TITLE;
    public static final String EXTRA_PLATFORM = FroglogMapActivity.EXTRA_PLATFORM;
    public static final String EXTRA_MINUTES = FroglogMapActivity.EXTRA_MINUTES;
    public static final String EXTRA_DATE = FroglogMapActivity.EXTRA_DATE;
    public static final String EXTRA_SYNC = FroglogMapActivity.EXTRA_SYNC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent map = new Intent(this, FroglogMapActivity.class);
        if (getIntent() != null && getIntent().getExtras() != null) {
            map.putExtras(getIntent().getExtras());
        }
        if (getIntent() != null) {
            map.setData(getIntent().getData());
        }
        startActivity(map);
        finish();
    }
}
