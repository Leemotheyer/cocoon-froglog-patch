package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/**
 * Base for Froglog screens: loads the Cocoon theme before views are built and maps
 * controller buttons the way Cocoon's own activities do (B is back, A activates focus).
 */
public class FroglogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FroglogTheme.load(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                onBackPressed();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_A) {
            View focus = getCurrentFocus();
            if (focus == null || !focus.isClickable()) {
                return super.dispatchKeyEvent(event);
            }
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                focus.performClick();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
