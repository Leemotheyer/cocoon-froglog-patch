import rip.moth.cocoonshell.froglog.FroglogNowPlaying;

public final class FroglogNowPlayingTest {
    public static void main(String[] args) {
        expect("game", FroglogNowPlaying.gameType(false));
        expect("live", FroglogNowPlaying.gameType(true));
        expect("game:12:1000", FroglogNowPlaying.key(12, false, 1000));
        expect("live:9:50", FroglogNowPlaying.key(9, true, 50));
        String started = FroglogNowPlaying.startedAt(1_000L);
        if (!started.startsWith("1970-01-01T00:00:01.000Z")) {
            throw new AssertionError("wanted UTC ISO instant, got " + started);
        }
        System.out.println("FroglogNowPlayingTest ok");
    }

    private static void expect(Object wanted, Object got) {
        if (wanted == null ? got != null : !wanted.equals(got)) {
            throw new AssertionError("wanted " + wanted + " got " + got);
        }
    }
}
