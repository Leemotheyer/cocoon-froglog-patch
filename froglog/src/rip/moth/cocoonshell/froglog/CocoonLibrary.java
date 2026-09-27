package rip.moth.cocoonshell.froglog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** Reads Cocoon's own game library. It does not write to that database. */
public final class CocoonLibrary {
    public static final class Game {
        public final String title;
        public final String platformId;
        public final String platformName;

        public Game(String title, String platformId, String platformName) {
            this.title = title;
            this.platformId = platformId;
            this.platformName = platformName == null || platformName.isEmpty() ? platformId : platformName;
        }
    }

    private CocoonLibrary() {}

    public static final class Playing {
        public final String title;
        public final String platformId;
        public final long startTimeMs;

        public Playing(String title, String platformId, long startTimeMs) {
            this.title = title;
            this.platformId = platformId == null ? "" : platformId;
            this.startTimeMs = startTimeMs;
        }
    }

    /** One finished row from {@code game_sessions}, the table Cocoon's Log pod lists. */
    public static final class Session {
        public final String clientSessionId;
        public final String title;
        public final String platformId;
        public final long startTime;
        public final long endTime;
        public final int durationMinutes;
        public final String date;

        public Session(String clientSessionId, String title, String platformId, long startTime, long endTime,
                int durationMinutes, String date) {
            this.clientSessionId = clientSessionId == null ? "" : clientSessionId;
            this.title = title == null ? "" : title.trim();
            this.platformId = platformId == null ? "" : platformId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
            this.date = date == null ? "" : date;
        }
    }

    /** Finished sessions that ended after {@code sinceMs}, oldest first. Null if the database could not be read. */
    public static List<Session> sessionsSince(Context context, long sinceMs) {
        ArrayList<Session> sessions = new ArrayList<Session>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            String path = context.getDatabasePath("cocoon_db").getPath();
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                    "SELECT clientSessionId, gameName, platformId, startTime, endTime, durationMinutes, date "
                            + "FROM game_sessions WHERE endTime > ? ORDER BY endTime ASC LIMIT 200",
                    new String[] {String.valueOf(sinceMs)});
            while (cursor.moveToNext()) {
                sessions.add(new Session(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                        cursor.getLong(3), cursor.getLong(4), cursor.getInt(5), cursor.getString(6)));
            }
        } catch (RuntimeException e) {
            android.util.Log.w("FroglogWidget", "game_sessions read failed", e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return sessions;
    }

    /**
     * The game Cocoon is tracking right now. Only RUNNING counts: PAUSED is the grace
     * window after the player leaves the game, and FINALIZING is the session closing.
     */
    public static Playing playing(Context context) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            String path = context.getDatabasePath("cocoon_db").getPath();
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                    "SELECT gameName, platformId, startTimeMs FROM pending_game_sessions "
                            + "WHERE finalizedAtMs IS NULL AND state = 'RUNNING' "
                            + "ORDER BY updatedAtMs DESC LIMIT 1",
                    null);
            if (!cursor.moveToFirst()) {
                return null;
            }
            String title = cursor.getString(0);
            if (title == null || title.trim().isEmpty()) {
                return null;
            }
            return new Playing(title.trim(), cursor.getString(1), cursor.getLong(2));
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
    }

    public static List<Game> recent(Context context) {
        ArrayList<Game> games = new ArrayList<Game>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            String path = context.getDatabasePath("cocoon_db").getPath();
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                    "SELECT COALESCE(NULLIF(games.title, ''), games.displayName), games.platformId, platforms.name "
                            + "FROM games LEFT JOIN platforms ON platforms.id = games.platformId "
                            + "WHERE games.isHidden = 0 ORDER BY games.lastPlayed DESC LIMIT 40",
                    null);
            while (cursor.moveToNext()) {
                String title = cursor.getString(0);
                if (title == null || title.trim().isEmpty()) {
                    continue;
                }
                games.add(new Game(title.trim(), cursor.getString(1), cursor.getString(2)));
            }
        } catch (RuntimeException ignored) {
            return games;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return games;
    }
}
