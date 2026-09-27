import rip.moth.cocoonshell.froglog.FroglogGame;
import rip.moth.cocoonshell.froglog.FroglogGames;

import java.util.List;

public final class FroglogGamesTest {
    public static void main(String[] args) {
        String games = "["
                + "{\"title\":\"Old Session\",\"last_session_date\":\"2024-01-02\",\"total_hours\":2,\"platform\":\"PC\",\"cover_image\":\"https://example/old.jpg\",\"status\":\"Completed\"},"
                + "{\"title\":\"Fresh Session\",\"last_session_date\":\"2026-09-01\",\"total_hours\":4.5,\"platform\":\"Switch\",\"img\":\"https://example/fresh.jpg\",\"status\":\"In Progress\"},"
                + "{\"title\":\"Never Played\",\"created_at\":\"2026-09-20T00:00:00.000Z\",\"hours_played\":0,\"status\":\"In Progress\"},"
                + "{\"title\":\"Finished Earlier\",\"end_date\":\"2026-08-01\",\"hours_played\":10,\"status\":\"Completed\"}"
                + "]";
        String live = "["
                + "{\"title\":\"Live Service\",\"last_session_date\":\"2026-09-10\",\"total_hours\":12,\"platform\":\"PC\",\"cover_image\":\"https://example/live.jpg\",\"live_service_status\":\"active\"}"
                + "]";
        List<FroglogGame> recent = FroglogGames.recent(games, live, 4);
        expect("Live Service", recent.get(0).title);
        expect("Fresh Session", recent.get(1).title);
        expect("Old Session", recent.get(2).title);
        expect("Finished Earlier", recent.get(3).title);
        expect("PC · Live · 12h", recent.get(0).meta);
        expect("Switch · In Progress · 4.5h", recent.get(1).meta);
        expect("https://example/fresh.jpg", recent.get(1).coverUrl);
        List<FroglogGame> playing = FroglogGames.recent(games, live, 4, FroglogGames.FILTER_PROGRESS);
        expect(2, Integer.valueOf(playing.size()));
        expect("Fresh Session", playing.get(0).title);
        expect("Never Played", playing.get(1).title);
        List<FroglogGame> liveOnly = FroglogGames.recent(games, live, 4, FroglogGames.FILTER_LIVE);
        expect("Live Service", liveOnly.get(0).title);
        expect(1, Integer.valueOf(liveOnly.size()));
        expect(true, FroglogGames.recent("{\"error\":\"nope\"}", "[]", 4).isEmpty());
        expect("4h", FroglogGames.hoursLabel(4.0));
        expect("4.5h", FroglogGames.hoursLabel(4.5));
        expect(null, FroglogGames.hoursLabel(0.0));
        System.out.println("FroglogGamesTest ok");
    }

    private static void expect(Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError("expected " + want + " but got " + got);
        }
    }
}
