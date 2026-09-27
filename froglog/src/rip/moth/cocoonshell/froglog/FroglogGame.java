package rip.moth.cocoonshell.froglog;

/** One Froglog game, reduced to what the widget and detail screen draw. */
public final class FroglogGame {
    public final long id;
    public final boolean live;
    public final String title;
    public final String platform;
    public final String coverUrl;
    public final String status;
    public final String review;
    public final Double rating;
    public final int sessionCount;
    public final String meta;
    public final long sortKey;

    public FroglogGame(long id, boolean live, String title, String platform, String coverUrl,
            String status, String review, Double rating, int sessionCount, String meta, long sortKey) {
        this.id = id;
        this.live = live;
        this.title = title;
        this.platform = platform;
        this.coverUrl = coverUrl;
        this.status = status;
        this.review = review == null ? "" : review;
        this.rating = rating;
        this.sessionCount = sessionCount;
        this.meta = meta;
        this.sortKey = sortKey;
    }
}
