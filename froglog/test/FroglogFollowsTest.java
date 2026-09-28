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
                + "{\"username\":\"bo\",\"displayUsername\":\"Bo\",\"online\":true,\"title\":\"Celeste II\",\"avatarUrl\":\"https://img/bo\"},"
                + "{\"username\":\"cy\",\"displayUsername\":\"Cy\",\"online\":true,\"title\":\"Hades II\",\"avatarUrl\":\"/uploads/avatars/cy.gif\"}"
                + "]}";
        List<FroglogFollow> people = FroglogFollows.people(activity, online, "me");
        // cy is online but not followed, so /activity/online alone does not add them.
        expect(2, people.size());
        expect("bo", people.get(0).username);
        expect("Playing Celeste II", people.get(0).status);
        expect(true, people.get(0).playing);
        expect("https://img/bo", people.get(0).avatarUrl);
        expect("ada", people.get(1).username);
        expect("Ada L", people.get(1).name);
        expect("Finished · Hades", people.get(1).status);
        expect(false, people.get(1).playing);
        List<FroglogFollow> followed = FroglogFollows.people(activity, online,
                "[{\"target_username\":\"cy\"},{\"target_username\":\"ada\"}]", "me");
        expect(2, followed.size());
        expect("cy", followed.get(0).username);
        expect("Playing Hades II", followed.get(0).status);
        expect("https://api.froglog.co.uk/uploads/avatars/cy.gif", followed.get(0).avatarUrl);
        expect("ada", followed.get(1).username);
        expect(0, FroglogFollows.people("{\"activity\":[]}", "[]", "me").size());
        String lastSeen = "{\"activity\":["
                + "{\"username\":\"lee\",\"display_username\":\"Lee\",\"game_title\":\"FINAL FANTASY XIV Online Free Trial\",\"type\":\"session_logged\",\"created_at\":\"2026-09-27\"}"
                + "]}";
        List<FroglogFollow> offline = FroglogFollows.people(lastSeen, lastSeen, "me");
        expect(1, offline.size());
        expect("lee", offline.get(0).username);
        expect("Logged · FINAL FANTASY XIV Online Free Trial", offline.get(0).status);
        expect(false, offline.get(0).playing);
        List<FroglogFollow> seen = FroglogFollows.people(lastSeen,
                "[{\"username\":\"lee\",\"displayUsername\":\"Lee\",\"online\":false,"
                        + "\"title\":\"FINAL FANTASY XIV Online Free Trial\"}]", "me");
        expect(false, seen.get(0).playing);
        List<FroglogFollow> untitled = FroglogFollows.people(lastSeen,
                "[{\"username\":\"lee\",\"displayUsername\":\"Lee\",\"online\":true,\"title\":null}]", "me");
        expect(true, untitled.get(0).playing);
        expect("In game", untitled.get(0).status);
        expect(null, untitled.get(0).game);
        System.out.println("FroglogFollowsTest ok");
    }

    private static void expect(Object wanted, Object got) {
        if (wanted == null ? got != null : !wanted.equals(got)) {
            throw new AssertionError("wanted " + wanted + " got " + got);
        }
    }
}
