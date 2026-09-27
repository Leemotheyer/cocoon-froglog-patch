package rip.moth.cocoonshell.froglog;

import android.content.Context;

import java.util.List;

/** Links a Cocoon title, then posts every waiting session for that title. */
public final class FroglogSubmit {
    public static final String NOTES = "Logged from Cocoon";
    public static final String NOTES_MAPPED = "Session logged from Cocoon";

    private FroglogSubmit() {}

    public static String send(Context context, String token, FroglogGame game, String title, String platform, String notes) {
        FroglogStore.link(context, FroglogMatch.linkKey(title, platform), game.id, game.live);
        List<FroglogQueue.Item> waiting = FroglogQueue.matching(FroglogStore.pending(context), title, platform);
        if (waiting.isEmpty()) {
            return null;
        }
        String note = notes == null || notes.isEmpty() ? NOTES_MAPPED : notes;
        FroglogGame target = game;
        String firstError = null;
        for (int i = 0; i < waiting.size(); i++) {
            FroglogQueue.Item item = waiting.get(i);
            if (item.minutes < 1 || item.sync.isEmpty()) {
                FroglogStore.removePending(context, item.sync);
                continue;
            }
            try {
                FroglogClient.Logged logged = FroglogClient.logSession(token, target, item.date,
                        FroglogMatch.hoursFromMinutes(item.minutes), "cocoon:" + item.sync, note);
                if (logged.live != target.live || logged.id != target.id) {
                    target = new FroglogGame(logged.id, logged.live, target.title, target.platform, target.coverUrl,
                            target.status, target.review, target.rating, target.sessionCount, target.meta, target.sortKey);
                    FroglogStore.link(context, FroglogMatch.linkKey(title, platform), logged.id, logged.live);
                }
                FroglogStore.markPosted(context, item.sync);
                FroglogStore.removePending(context, item.sync);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not log the session" : e.getMessage();
                FroglogStore.pendingError(context, item.sync, message);
                if (firstError == null) {
                    firstError = message;
                }
            }
        }
        return firstError;
    }
}
