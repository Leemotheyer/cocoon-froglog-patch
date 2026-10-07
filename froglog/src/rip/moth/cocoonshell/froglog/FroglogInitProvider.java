package rip.moth.cocoonshell.froglog;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Runs before the UI so the Froglog catalog tiles and pod exist the first time they are shown. */
public final class FroglogInitProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        FroglogSetup.skipIfRequested(getContext());
        CatalogHook.install(getContext());
        FroglogPods.install(getContext());
        FroglogSocial.warm(getContext());
        FroglogPresence.start(getContext());
        FroglogWidgetTheme.watch(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
