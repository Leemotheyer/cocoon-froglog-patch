package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;

/** Puts "Upload to Froglog" at the top of Picnic's share sheet. Called from cf.pi.Z0. */
public final class FroglogPicnic {
    private static final String TAG = "FroglogPicnic";
    private static final int POD_ICON = 0x7F060218;

    private FroglogPicnic() {}

    public static Intent withUpload(Intent chooser, Context context, Intent send, String source) {
        if (chooser == null || context == null || send == null) {
            return chooser;
        }
        try {
            Object stream = send.getParcelableExtra(Intent.EXTRA_STREAM);
            if (!(stream instanceof Uri)) {
                return chooser;
            }
            Intent upload = new Intent(context, FroglogPicnicActivity.class)
                    .putExtra(FroglogPicnicActivity.EXTRA_URI, stream.toString())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            LabeledIntent labeled = new LabeledIntent(upload, context.getPackageName(), "Upload to Froglog", POD_ICON);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[] {labeled});
        } catch (RuntimeException error) {
            Log.w(TAG, "share sheet left as is (" + source + ")", error);
        }
        return chooser;
    }
}
