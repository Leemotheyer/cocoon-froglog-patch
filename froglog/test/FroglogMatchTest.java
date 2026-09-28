import rip.moth.cocoonshell.froglog.FroglogGame;
import rip.moth.cocoonshell.froglog.FroglogMatch;

import java.util.Arrays;

public final class FroglogMatchTest {
    public static void main(String[] args) {
        FroglogGame zelda = game(4, "The Legend of Zelda", "Switch");
        FroglogGame other = game(9, "Zelda's Adventure", "NES");
        FroglogGame best = FroglogMatch.best(Arrays.asList(other, zelda), "The Legend of Zelda", "Switch");
        expect("The Legend of Zelda", best.title);
        expect(true, FroglogMatch.best(Arrays.asList(zelda), "Metroid", "SNES") == null);
        expect(0.75, Double.valueOf(FroglogMatch.hoursFromMinutes(45)));
        expect("the legend of zelda|switch", FroglogMatch.linkKey("The Legend of Zelda", "Switch"));
        expect("hades", FroglogMatch.normalizeTitle("Hades: Definitive Edition"));
        expect("hollow knight voidheart edition", FroglogMatch.normalizeTitle("Hollow Knight: Voidheart Edition"));
        expect("the witcher 3 wild hunt", FroglogMatch.normalizeTitle("The Witcher 3: Wild Hunt GOTY Edition"));
        FroglogGame hades = game(3, "Hades", "PC", "In Progress");
        FroglogGame finished = game(9, "Hades", "PC", "Completed");
        FroglogGame mapped = FroglogMatch.best(Arrays.asList(finished, hades), "Hades: Definitive Edition", "PC");
        expect("In Progress", mapped.status);
        expect(Long.valueOf(3), Long.valueOf(mapped.id));
        FroglogGame emerald = game(11, "Pokémon Emerald Version", "Game Boy Advance");
        FroglogGame alttp = game(12, "The Legend of Zelda: A Link to the Past", "SNES");
        FroglogGame smw = game(13, "Super Mario World", "SNES");
        java.util.List<FroglogGame> roms = Arrays.asList(emerald, alttp, smw);
        expect(Long.valueOf(11), Long.valueOf(FroglogMatch.best(roms,
                "Pokemon - Emerald Version (USA, Europe) (Rev 1)", "gba").id));
        expect(Long.valueOf(12), Long.valueOf(FroglogMatch.best(roms,
                "Legend of Zelda, The - A Link to the Past (USA)", "snes").id));
        expect(Long.valueOf(13), Long.valueOf(FroglogMatch.best(roms, "Super Mario World [!]", "snes").id));
        expect("the legend of zelda|switch", FroglogMatch.linkKey("The Legend of Zelda", "Switch"));
        System.out.println("FroglogMatchTest ok");
    }

    private static FroglogGame game(long id, String title, String platform) {
        return game(id, title, platform, "In Progress");
    }

    private static FroglogGame game(long id, String title, String platform, String status) {
        return new FroglogGame(id, false, title, platform, null, status, "", null, 0, "", 0);
    }

    private static void expect(Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError("expected " + want + " but got " + got);
        }
    }
}
