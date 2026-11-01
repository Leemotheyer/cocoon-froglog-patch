package rip.moth.cocoonshell.froglog;

/** Someone the signed-in user follows on Froglog, plus the latest thing they did. */
public final class FroglogFollow {
    public final String username;
    public final String name;
    public final String avatarUrl;
    public final String status;
    public final String game;
    public final boolean playing;
    /** Wide game art for the friends row, the same role as a Steam friend's game background. */
    public final String artwork;

    public FroglogFollow(String username, String name, String avatarUrl, String status, String game, boolean playing) {
        this(username, name, avatarUrl, status, game, playing, null);
    }

    public FroglogFollow(String username, String name, String avatarUrl, String status, String game, boolean playing,
            String artwork) {
        this.username = username;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.status = status;
        this.game = game;
        this.playing = playing;
        this.artwork = artwork;
    }
}
