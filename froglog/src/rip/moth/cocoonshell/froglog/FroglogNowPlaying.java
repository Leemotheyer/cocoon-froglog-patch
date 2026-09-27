package rip.moth.cocoonshell.froglog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** LilyPad-compatible now-playing fields. No Android types so unit tests can call it. */
public final class FroglogNowPlaying {
    private FroglogNowPlaying() {}

    public static String gameType(boolean live) {
        return live ? "live" : "game";
    }

    public static String startedAt(long startTimeMs) {
        if (startTimeMs <= 0) {
            startTimeMs = System.currentTimeMillis();
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(startTimeMs));
    }

    public static String key(long gameId, boolean live, long startTimeMs) {
        return gameType(live) + ":" + gameId + ":" + startTimeMs;
    }
}
