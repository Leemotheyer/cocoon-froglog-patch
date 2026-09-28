import org.json.JSONObject;

import rip.moth.cocoonshell.froglog.FroglogCreate;
import rip.moth.cocoonshell.froglog.FroglogGame;
import rip.moth.cocoonshell.froglog.FroglogTracking;

import java.util.Arrays;

public final class FroglogTrackingTest {
    public static void main(String[] args) throws Exception {
        JSONObject ready = new JSONObject();
        ready.put("id", 4);
        ready.put("title", "Hades");
        ready.put("status", "In Progress");
        ready.put("session_tracking", true);
        ready.put("start_date", "2026-01-01");
        ready.put("total_hours", 3);
        expect(true, FroglogTracking.preparePayload(ready, "2026-09-01") == null);

        JSONObject plain = new JSONObject();
        plain.put("id", 4);
        plain.put("title", "Hades");
        plain.put("status", "In Progress");
        plain.put("session_tracking", false);
        plain.put("hours_played", "2.50");
        plain.put("total_hours", 9);
        JSONObject enabled = FroglogTracking.preparePayload(plain, "2026-09-01");
        expect(true, Boolean.valueOf(enabled.getBoolean("session_tracking")));
        expect(true, Boolean.valueOf(enabled.getBoolean("sessions_public")));
        expect(Double.valueOf(2.5), Double.valueOf(enabled.getDouble("initial_session_hours")));
        expect(false, Boolean.valueOf(enabled.has("total_hours")));

        JSONObject finished = new JSONObject();
        finished.put("id", 8);
        finished.put("status", "Completed");
        finished.put("session_tracking", true);
        finished.put("start_date", "2025-12-01");
        finished.put("dnf", false);
        finished.put("end_date", "2026-01-01");
        JSONObject resumed = FroglogTracking.preparePayload(finished, "2026-09-01");
        expect(JSONObject.NULL, resumed.get("end_date"));
        expect(JSONObject.NULL, resumed.get("status_override"));
        expect(false, Boolean.valueOf(resumed.getBoolean("dnf")));
        expect(false, Boolean.valueOf(resumed.has("initial_session_hours")));
        expect(null, FroglogTracking.preparePayload(finished, "2026-09-01", false));

        JSONObject untracked = new JSONObject();
        untracked.put("id", 14342);
        untracked.put("status", "");
        untracked.put("session_tracking", false);
        untracked.put("start_date", JSONObject.NULL);
        untracked.put("public_session_count", "0");
        JSONObject repaired = FroglogTracking.preparePayload(untracked, "2026-09-27", false);
        expect("2026-09-27", repaired.getString("start_date"));
        expect(true, Boolean.valueOf(repaired.getBoolean("session_tracking")));
        expect(false, Boolean.valueOf(repaired.has("public_session_count")));

        JSONObject imported = new JSONObject();
        imported.put("id", 2);
        imported.put("status", "Imported");
        imported.put("session_tracking", true);
        imported.put("start_date", JSONObject.NULL);
        JSONObject started = FroglogTracking.preparePayload(imported, "2026-09-27");
        expect("2026-09-27", started.getString("start_date"));

        JSONObject fresh = new JSONObject();
        fresh.put("id", 3);
        fresh.put("status", "In Progress");
        fresh.put("session_tracking", true);
        JSONObject dated = FroglogTracking.preparePayload(fresh, "2026-09-27");
        expect("2026-09-27", dated.getString("start_date"));

        expect(Integer.valueOf(5), Integer.valueOf(FroglogTracking.playMinutes(5, 0, 0)));
        expect(Integer.valueOf(0), Integer.valueOf(FroglogTracking.playMinutes(0, 1000, 1000)));
        expect(Integer.valueOf(0), Integer.valueOf(FroglogTracking.playMinutes(0, 1000, 1000 + 10_000)));
        expect(Integer.valueOf(1), Integer.valueOf(FroglogTracking.playMinutes(0, 1000, 1000 + 22_000)));
        expect(Integer.valueOf(1), Integer.valueOf(FroglogTracking.playMinutes(0, 1000, 1000 + 60_000)));
        expect(Integer.valueOf(2), Integer.valueOf(FroglogTracking.playMinutes(0, 1000, 1000 + 90_000)));

        FroglogGame one = new FroglogGame(5, true, "Hades", "PC", null, "Live", "", null, 0, "", 0);
        FroglogGame two = new FroglogGame(6, true, "Hades", "PC", null, "Live", "", null, 0, "", 0);
        expect(Long.valueOf(5), FroglogTracking.uniqueLiveId(Arrays.asList(one), "Hades: Definitive Edition"));
        expect(true, FroglogTracking.uniqueLiveId(Arrays.asList(one, two), "Hades") == null);

        FroglogCreate.Outcome created = FroglogCreate.interpret(201, "{\"id\":4}");
        expect(true, Boolean.valueOf(created.created()));
        expect(Long.valueOf(4), Long.valueOf(created.createdId));
        FroglogCreate.Outcome choice = FroglogCreate.interpret(409,
                "{\"needs_confirmation\":true,\"existing_game\":{\"id\":9,\"title\":\"Hades\"}}");
        expect(true, Boolean.valueOf(choice.needsChoice()));
        expect(Long.valueOf(9), Long.valueOf(choice.existingId));
        expect("Hades", choice.existingTitle);
        FroglogCreate.Outcome other = FroglogCreate.interpret(409, "{\"error\":\"Conflict\"}");
        expect("Conflict", other.error);
        FroglogCreate.Outcome limited = FroglogCreate.interpret(429, "{}");
        expect(true, Boolean.valueOf(limited.error.contains("limiting")));
        System.out.println("FroglogTrackingTest ok");
    }

    private static void expect(Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError("expected " + want + " but got " + got);
        }
    }
}
