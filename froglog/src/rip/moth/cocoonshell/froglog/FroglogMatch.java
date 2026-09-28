package rip.moth.cocoonshell.froglog;

import java.text.Normalizer;
import java.util.List;

/** Picks the Froglog library entry that corresponds to a Cocoon game. */
public final class FroglogMatch {
    private static final String[] EDITION_SUFFIXES = {
            "game of the year edition",
            "goty edition",
            "definitive edition",
            "complete edition",
            "deluxe edition",
            "ultimate edition",
            "enhanced edition",
            "remastered",
            "remake"
    };

    private FroglogMatch() {}

    public static FroglogGame best(List<FroglogGame> library, String title, String platform) {
        FroglogGame best = null;
        int bestScore = 0;
        String wanted = normalizeTitle(title);
        String wantedPlatform = normalize(platform);
        if (wanted.isEmpty() || library == null) {
            return null;
        }
        for (FroglogGame game : library) {
            int score = score(wanted, wantedPlatform, game);
            if (score > bestScore || (score == bestScore && score > 0 && best != null && prefer(game, best))) {
                bestScore = score;
                best = game;
            }
        }
        return bestScore >= 80 ? best : null;
    }

    public static String linkKey(String title, String platform) {
        return normalize(title) + "|" + normalize(platform);
    }

    public static double hoursFromMinutes(int minutes) {
        if (minutes <= 0) {
            return 0;
        }
        return Math.round(minutes * 100.0 / 60.0) / 100.0;
    }

    static int score(String wantedTitle, String wantedPlatform, FroglogGame game) {
        String title = normalizeTitle(game.title);
        if (title.isEmpty()) {
            return 0;
        }
        int score = 0;
        if (title.equals(wantedTitle)) {
            score = 100;
        } else if (title.contains(wantedTitle) || wantedTitle.contains(title)) {
            score = 80;
        } else {
            return 0;
        }
        String platform = normalize(game.platform);
        if (!wantedPlatform.isEmpty() && !platform.isEmpty() && platform.equals(wantedPlatform)) {
            score += 10;
        }
        return score;
    }

    /**
     * Same cleanup as {@link #normalize}, then edition suffixes such as "definitive edition".
     * Emulator ROM names are also reduced to the game's title: No-Intro tags like
     * {@code (USA, Europe) (Rev 1)} go, accents fold, and {@code Legend of Zelda, The} reads
     * as {@code The Legend of Zelda}.
     */
    public static String normalizeTitle(String value) {
        String normalized = normalize(romTitle(value));
        for (int i = 0; i < EDITION_SUFFIXES.length; i++) {
            String suffix = EDITION_SUFFIXES[i];
            if (normalized.endsWith(suffix)) {
                normalized = normalized.substring(0, normalized.length() - suffix.length()).trim();
            }
        }
        return normalized;
    }

    static String romTitle(String value) {
        if (value == null) {
            return "";
        }
        String title = value.replaceAll("[\\(\\[][^\\)\\]]*[\\)\\]]", " ").trim();
        if (title.isEmpty()) {
            title = value;
        }
        title = title.replaceAll("^(.+?), (The|A|An)(\\s*(?:[-:].*)?)$", "$2 $1$3");
        return Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                space = false;
            } else if (!space && out.length() > 0) {
                out.append(' ');
                space = true;
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') {
            end--;
        }
        return out.substring(0, end);
    }

    private static boolean prefer(FroglogGame candidate, FroglogGame current) {
        int rank = Integer.compare(statusRank(candidate.status), statusRank(current.status));
        if (rank != 0) {
            return rank > 0;
        }
        return candidate.id > current.id;
    }

    private static int statusRank(String status) {
        if ("In Progress".equals(status)) {
            return 3;
        }
        if ("Dormant".equals(status)) {
            return 2;
        }
        if ("Imported".equals(status)) {
            return 1;
        }
        return 0;
    }
}
