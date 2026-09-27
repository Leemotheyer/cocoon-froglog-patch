package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

/**
 * Widget configure. Opens the Froglog pod only when the user still needs to sign in.
 */
public class FroglogWidgetConfig extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent incoming = getIntent();
        if (incoming != null && incoming.getExtras() != null) {
            appWidgetId = incoming.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }
        if (FroglogStore.signedIn(this)) {
            finishOk();
            return;
        }
        Intent pod = new Intent(this, FroglogPodActivity.class);
        if (incoming != null && incoming.getExtras() != null) {
            pod.putExtras(incoming.getExtras());
        }
        pod.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        startActivityForResult(pod, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            data = new Intent();
        }
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && !data.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
            data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }
        setResult(resultCode, data);
        finish();
    }

    private void finishOk() {
        Intent result = new Intent();
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }
        setResult(RESULT_OK, result);
        finish();
    }
}
