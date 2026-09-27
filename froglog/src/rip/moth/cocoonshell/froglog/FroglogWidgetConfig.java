package rip.moth.cocoonshell.froglog;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

/** Older entry point. Account management lives in the Froglog pod. */
public class FroglogWidgetConfig extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent incoming = getIntent();
        int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        if (incoming != null && incoming.getExtras() != null) {
            appWidgetId = incoming.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }
        Intent pod = new Intent(this, FroglogPodActivity.class);
        if (incoming != null && incoming.getExtras() != null) {
            pod.putExtras(incoming.getExtras());
        }
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
        } else {
            setResult(RESULT_CANCELED);
        }
        startActivity(pod);
        finish();
    }
}
