import rip.moth.cocoonshell.froglog.FroglogQueue;

import java.util.List;

public final class FroglogQueueTest {
    public static void main(String[] args) {
        String json = FroglogQueue.upsert("[]", "Hades", "pc", 40, "2026-09-01", "a");
        json = FroglogQueue.upsert(json, "Hades", "pc", 15, "2026-09-02", "b");
        List<FroglogQueue.Item> items = FroglogQueue.parse(json);
        expect(2, Integer.valueOf(items.size()));
        expect("b", items.get(0).sync);
        json = FroglogQueue.upsert(json, "Hades", "pc", 20, "2026-09-03", "a");
        json = FroglogQueue.rememberError(json, "a", "offline");
        items = FroglogQueue.parse(json);
        expect(2, Integer.valueOf(items.size()));
        FroglogQueue.Item again = null;
        for (int i = 0; i < items.size(); i++) {
            if ("a".equals(items.get(i).sync)) {
                again = items.get(i);
            }
        }
        expect(Integer.valueOf(20), Integer.valueOf(again.minutes));
        expect("offline", again.error);
        expect(2, Integer.valueOf(FroglogQueue.matching(items, "Hades", "PC").size()));
        expect(0, Integer.valueOf(FroglogQueue.matching(items, "Celeste", "pc").size()));
        json = FroglogQueue.remove(json, "b");
        expect(false, Boolean.valueOf(FroglogQueue.contains(json, "b")));
        json = FroglogQueue.removeKey(json, "Hades", "pc");
        expect(0, Integer.valueOf(FroglogQueue.parse(json).size()));
        String many = "[]";
        for (int i = 0; i < FroglogQueue.MAX + 1; i++) {
            many = FroglogQueue.upsert(many, "Game", "pc", 5, "2026-09-01", "s" + i);
        }
        List<FroglogQueue.Item> capped = FroglogQueue.parse(many);
        expect(Integer.valueOf(FroglogQueue.MAX), Integer.valueOf(capped.size()));
        expect("s" + FroglogQueue.MAX, capped.get(0).sync);
        expect(false, Boolean.valueOf(FroglogQueue.contains(many, "s0")));
        System.out.println("FroglogQueueTest ok");
    }

    private static void expect(Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError("expected " + want + " but got " + got);
        }
    }
}
