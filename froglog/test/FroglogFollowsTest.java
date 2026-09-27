import rip.moth.cocoonshell.froglog.FroglogFollow;
import rip.moth.cocoonshell.froglog.FroglogFollows;

import java.util.List;

public final class FroglogFollowsTest {
    public static void main(String[] args) {
        String activity = "{"
                + "\"activity\":["
                + "{\"username\":\"me\",\"display_username\":\"Me\",\"game_title\":\"Skip\",\"type\":\"session_logged\",\"created_at\":\"2026-09-01\"},"
                + "{\"username\":\"ada\",\"display_username\":\"Ada\",\"avatar_url\":\"https://img/ada\",\"game_title\":\"Old Game\",\"type\":\"session_logged\",\"created_at\":\"2026-08-01\"},"
                + "{\"username\":\"ada\",\"nickname\":\"Ada L\",\"game_title\":\"Hades\",\"type\":\"game_completed\",\"created_at\":\"2026-09-02\"},"
                + "{\"username\":\"bo\",\"display_username\":\"Bo\",\"type\":\"game_started\",\"created_at\":\"2026-09-03\",\"game_title\":\"Celeste\"}"
                + "]}";
        String online = "{\"online\":["
                + "{\"username\":\"bo\",\"displayUsername\":\"Bo\",\"title\":\"Celeste II\",\"avatarUrl\":\"https://img/bo\"},"
                + "{\"username\":\"cy\",\"displayUsername\":\"Cy\",\"title\":\"Hades II\"}"
                + "]}";
        List<FroglogFollow> people = FroglogFollows.people(activity, online, "me");
        expect(3, people.size());
        expect("ada", people.get(0).username);
        expect("Ada L", people.get(0).name);
        expect("Finished · Hades", people.get(0).status);
        expect(false, people.get(0).playing);
        expect("bo", people.get(1).username);
        expect("Playing Celeste II", people.get(1).status);
        expect(true, people.get(1).playing);
        expect("https://img/bo", people.get(1).avatarUrl);
        expect("cy", people.get(2).username);
        expect("Playing Hades II", people.get(2).status);
        expect(true, people.get(2).playing);
        expect(0, FroglogFollows.people("{\"activity\":[]}", "[]", "me").size());
        String lastSeen = "{\"activity\":["
                + "{\"username\":\"lee\",\"display_username\":\"Lee\",\"game_title\":\"FINAL FANTASY XIV Online Free Trial\",\"type\":\"session_logged\",\"created_at\":\"2026-09-27\"}"
                + "]}";
        List<FroglogFollow> offline = FroglogFollows.people(lastSeen, lastSeen, "me");
        expect(1, offline.size());
        expect("lee", offline.get(0).username);
        expect("Logged · FINAL FANTASY XIV Online Free Trial", offline.get(0).status);
        expect(false, offline.get(0).playing);
        System.out.println("FroglogFollowsTest ok");
    }

    private static void expect(Object wanted, Object got) {
        if (wanted == null ? got != null : !wanted.equals(got)) {
            throw new AssertionError("wanted " + wanted + " got " + got);
        }
    }
}
