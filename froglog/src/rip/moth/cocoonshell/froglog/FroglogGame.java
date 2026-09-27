package rip.moth.cocoonshell.froglog;

/** One public Froglog game, reduced to what the recent-games widget draws. */
public final class FroglogGame {
    public final String title;
    public final String platform;
    public final String coverUrl;
    public final String meta;
    public final long sortKey;

    public FroglogGame(String title, String platform, String coverUrl, String meta, long sortKey) {
        this.title = title;
        this.platform = platform;
        this.coverUrl = coverUrl;
        this.meta = meta;
        this.sortKey = sortKey;
    }
}
