package rip.moth.cocoonshell.froglog;

import android.content.Context;

import jb.a;

/** Opens {@link FroglogPicnicActivity} from Picnic's screenshot info dialog. */
public final class FroglogPicnicUpload implements a {
    private final Context context;
    private final Object picnicDetail;

    public FroglogPicnicUpload(Context context, Object picnicDetail) {
        this.context = context;
        this.picnicDetail = picnicDetail;
    }

    @Override
    public Object invoke() {
        FroglogPicnic.openUpload(context, picnicDetail);
        return null;
    }
}
