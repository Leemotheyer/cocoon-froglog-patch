import org.json.JSONArray;
import org.json.JSONObject;

import rip.moth.cocoonshell.froglog.FroglogGame;
import rip.moth.cocoonshell.froglog.FroglogGameInfo;
import rip.moth.cocoonshell.froglog.FroglogGames;

import java.util.List;

public final class FroglogGameInfoTest {
    public static void main(String[] args) throws Exception {
        JSONObject game = new JSONObject("{"
                + "\"id\":12,\"user_id\":3,\"title\":\"Hades\",\"platform\":\"PC\",\"status\":\"Completed\","
                + "\"total_hours\":41.5,\"session_count\":9,\"rating\":4.5,\"start_date\":\"2026-08-01T10:00:00.000Z\","
                + "\"end_date\":\"2026-09-20\",\"last_session_date\":null,\"genres\":[{\"name\":\"Roguelike\"},{\"name\":\"Action\"}],"
                + "\"cover_image\":\"/uploads/covers/hades.jpg\",\"review\":\"Great\",\"igdb_id\":1113,"
                + "\"store_url\":\"https://example\",\"replayed\":true,\"difficulty\":\"Hard\",\"created_at\":\"2026-07-30\"}");
        List<String[]> rows = FroglogGameInfo.rows(game);
        expect("Status|Completed", row(rows, 0));
        expect("Platform|PC", row(rows, 1));
        expect("Hours played|41.5h", row(rows, 2));
        expect("Sessions|9", row(rows, 3));
        expect("Rating|★4.5", row(rows, 4));
        expect("Started|2026-08-01", row(rows, 5));
        expect("Finished|2026-09-20", row(rows, 6));
        expect("Genre|Roguelike, Action", row(rows, 7));
        expect("Added|2026-07-30", row(rows, 8));
        expect("Difficulty|Hard", row(rows, 9));
        expect("Replayed|Yes", row(rows, 10));
        expect(11, rows.size());

        List<String[]> catalog = FroglogGameInfo.rows(new JSONObject(
                "{\"rating\":90,\"dev\":\"Supergiant\",\"rel_date\":\"2020-09-17\",\"description\":\"Defy\"}"));
        expect("Rating|★4.5", row(catalog, 0));
        expect("Developer|Supergiant", row(catalog, 1));
        expect("Released|2020-09-17", row(catalog, 2));
        expect(3, catalog.size());
        expect("Defy", FroglogGameInfo.description(new JSONObject("{\"description\":\"Defy\"}")));

        JSONObject live = new JSONObject("{\"title\":\"FFXIV\",\"live_service_status\":\"active\",\"session_count\":0}");
        List<String[]> liveRows = FroglogGameInfo.rows(live);
        expect(1, liveRows.size());
        expect("Status|Live", row(liveRows, 0));
        expect(0, FroglogGameInfo.rows(null).size());

        List<String[]> sessions = FroglogGameInfo.sessions(new JSONArray("["
                + "{\"date\":\"2026-09-01\",\"hours\":2,\"notes\":null},"
                + "{\"date\":\"2026-09-12T08:00:00Z\",\"hours\":1.25,\"notes\":\" Boss \"}"
                + "]"));
        expect(2, sessions.size());
        expect("2026-09-12", sessions.get(0)[0]);
        expect("1.3h", sessions.get(0)[1]);
        expect("Boss", sessions.get(0)[2]);
        expect("2h", sessions.get(1)[1]);
        expect("", sessions.get(1)[2]);

        List<FroglogGame> games = FroglogGames.recent(
                "[{\"id\":4,\"title\":\"Hades\",\"genre\":\"Roguelike\"}]", "[]", 5);
        expect(true, new JSONObject(games.get(0).json).optString("genre").equals("Roguelike"));
        System.out.println("FroglogGameInfoTest ok");
    }

    private static String row(List<String[]> rows, int index) {
        return rows.get(index)[0] + "|" + rows.get(index)[1];
    }

    private static void expect(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
