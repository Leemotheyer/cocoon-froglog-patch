import rip.moth.cocoonshell.froglog.FroglogGame;
import rip.moth.cocoonshell.froglog.FroglogLinks;
import rip.moth.cocoonshell.froglog.FroglogMatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FroglogLinksTest {
    public static void main(String[] args) {
        String hades = FroglogMatch.linkKey("Hades", "shortcut");
        String celeste = FroglogMatch.linkKey("Celeste", "switch");
        String old = FroglogMatch.linkKey("Balatro", "android");
        String skip = FroglogMatch.linkKey("Launcher Test", "android");
        String unknown = FroglogMatch.linkKey("Mystery Game", "ps2");

        Map<String, Object> prefs = new HashMap<String, Object>();
        prefs.put("token", "jwt");
        prefs.put(FroglogLinks.LINK + hades, FroglogLinks.value(12, false));
        prefs.put(FroglogLinks.NAME + hades, FroglogLinks.encodeName("Hades", "shortcut"));
        prefs.put(FroglogLinks.LINK + celeste, FroglogLinks.value(40, true));
        prefs.put(FroglogLinks.NAME + celeste, FroglogLinks.encodeName("Celeste", "switch"));
        prefs.put(FroglogLinks.LINK + old, "game:7");
        prefs.put(FroglogLinks.LINK + skip, FroglogLinks.DECLINED);
        prefs.put(FroglogLinks.NAME + skip, FroglogLinks.encodeName("Launcher Test", "android"));
        prefs.put(FroglogLinks.LINK + unknown, "game:9");
        prefs.put(FroglogLinks.LINK + "broken|x", "game:abc");
        prefs.put(FroglogLinks.NAME + "orphan|x", FroglogLinks.encodeName("Orphan", "x"));

        Map<String, String[]> fallback = new HashMap<String, String[]>();
        fallback.put(old, new String[] {"Balatro", "android"});

        List<FroglogLinks.Mapping> list = FroglogLinks.read(prefs, fallback);
        expect(Integer.valueOf(5), Integer.valueOf(list.size()));
        expect("Balatro", list.get(0).title);
        expect("Celeste", list.get(1).title);
        expect(true, Boolean.valueOf(list.get(1).live));
        expect(Long.valueOf(40), Long.valueOf(list.get(1).gameId));
        expect("Hades", list.get(2).title);
        expect("shortcut", list.get(2).platform);
        expect("mystery game", list.get(3).title);
        expect("ps2", list.get(3).platform);
        expect("Launcher Test", list.get(4).title);
        expect(true, Boolean.valueOf(list.get(4).declined));

        expect(unknown, FroglogMatch.linkKey(list.get(3).title, list.get(3).platform));

        FroglogGame regular = new FroglogGame(40, false, "Celeste", "PC", null, "", "", null, 0, "", 0);
        FroglogGame live = new FroglogGame(40, true, "Celeste Live", "PC", null, "", "", null, 0, "", 0);
        expect(live, FroglogLinks.find(Arrays.asList(regular, live), list.get(1)));
        expect(null, FroglogLinks.find(Arrays.asList(regular), list.get(1)));
        expect(null, FroglogLinks.find(Arrays.asList(regular, live), list.get(4)));

        expect(null, FroglogLinks.decodeName("not json"));
        expect(null, FroglogLinks.decodeName(null));
        System.out.println("FroglogLinksTest ok");
    }

    private static void expect(Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError("expected " + want + " but got " + got);
        }
    }
}
