import rip.moth.cocoonshell.froglog.FroglogStatsSummary;

public final class FroglogStatsSummaryTest {
    public static void main(String[] args) throws Exception {
        FroglogStatsSummary camel = new FroglogStatsSummary("2026-10-07");
        camel.readStats("{\"totalGames\":40,\"completed\":10,\"completionRate\":25,\"totalHours\":\"512.5\","
                + "\"thisMonth\":{\"total\":3,\"completed\":1,\"hours\":\"12.5\"},"
                + "\"thisYear\":{\"total\":20,\"completed\":6,\"hours\":140},"
                + "\"sessionHeatmap\":["
                + "{\"day\":\"2026-10-07\",\"hours\":1,\"sessions\":1},"
                + "{\"day\":\"2026-10-06\",\"hours\":2,\"sessions\":1},"
                + "{\"day\":\"2026-10-05T00:00:00.000Z\",\"hours\":\"0.5\",\"sessions\":1},"
                + "{\"day\":\"2026-10-03\",\"hours\":3,\"sessions\":2},"
                + "{\"day\":\"2026-09-20\",\"hours\":9,\"sessions\":2}],"
                + "\"lsStats\":{\"sessionHeatmap\":[{\"day\":\"2026-10-04\",\"hours\":1,\"sessions\":1}]}}");
        expect(12.5, camel.monthHours);
        expect(140.0, camel.yearHours);
        expect(6, camel.yearCompleted);
        expect(25, camel.completionRate);
        expect(512.5, camel.totalHours);
        expect(40, camel.totalGames);
        // Live service play on the 4th joins the 3rd..7th into one run.
        expect(5, camel.streak);
        expect(7.5, camel.weekHours);
        double[] days = camel.lastDays(7);
        expect(7, days.length);
        expect(1.0, days[6]);
        expect(2.0, days[5]);
        expect(0.5, days[4]);
        expect(1.0, days[3]);
        expect(0.0, days[0]);
        // 2026-10-07 is a Wednesday.
        String[] letters = camel.lastDayLetters(7);
        expect("W", letters[6]);
        expect("T", letters[5]);
        expect("T", letters[0]);

        FroglogStatsSummary snake = new FroglogStatsSummary("2026-10-07");
        snake.readStats("{\"this_month\":{\"hours\":4,\"completed\":2},\"this_year\":{\"hours\":30},"
                + "\"overall\":{\"completion_rate\":0.5},"
                + "\"sessionHeatmap\":[{\"day\":\"2026-10-06\",\"hours\":1,\"sessions\":1},"
                + "{\"day\":\"2026-10-05\",\"hours\":1,\"sessions\":1}]}");
        expect(4.0, snake.monthHours);
        expect(2, snake.monthCompleted);
        expect(50, snake.completionRate);
        // Nothing yet today: yesterday's run still counts.
        expect(2, snake.streak);

        FroglogStatsSummary empty = new FroglogStatsSummary("2026-10-07");
        empty.readStats("{}");
        expect(0, empty.streak);
        expect(-1, empty.completionRate);

        FroglogStatsSummary top = new FroglogStatsSummary("2026-10-07");
        boolean more = top.addSessions("{\"sessions\":["
                + "{\"date\":\"2026-10-06\",\"hours\":\"2.5\",\"game_id\":12,\"title\":\"Hades II\",\"img\":\"https://x/h.jpg\"},"
                + "{\"date\":\"2026-10-02\",\"hours\":\"1\",\"game_id\":7,\"title\":\"Celeste\"},"
                + "{\"date\":\"2026-10-01\",\"hours\":\"2\",\"game_id\":12,\"title\":\"Hades II\"}],"
                + "\"page\":1,\"totalPages\":3}", false);
        expect(true, more);
        boolean after = top.addSessions("{\"sessions\":["
                + "{\"date\":\"2026-10-03\",\"hours\":\"3\",\"live_service_id\":4,\"title\":\"Fortnite\"},"
                + "{\"date\":\"2026-09-29\",\"hours\":\"9\",\"live_service_id\":4,\"title\":\"Fortnite\"}],"
                + "\"page\":1,\"totalPages\":2}", true);
        expect(false, after);
        top.finishTop();
        expect("Hades II", top.topTitle);
        expect(4.5, top.topHours);
        expect(12L, top.topId);
        expect(false, top.topLive);
        expect("https://x/h.jpg", top.topCover);

        System.out.println("FroglogStatsSummaryTest ok");
    }

    private static void expect(Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError("expected " + want + " but got " + got);
        }
    }
}
