package bms.player.beatoraja.nonstop;

/**
 * One song in a {@link MixPlan}. The chart is identified by its hashes, so a
 * shared recipe can be matched against another player's library; the local
 * path is only a hint and is never exported.
 */
public final class MixTrack {
    public String sha256 = "";
    public String md5 = "";
    public String title = "";
    public String subtitle = "";
    public String artist = "";
    public String genre = "";
    /** Display BPM at the time the track was added. */
    public double bpm;
    /** Chart level shown in the editor. */
    public int level;

    /** First used chart time (ms). */
    public long inMs;
    /** Last used chart time (ms); a negative value means "to the end of the chart". */
    public long outMs = -1;
    /** Extra gain applied to this song, in dB. */
    public double gainDb;
    /** Fixed #RANDOM branch; null uses the chart's default draw. */
    public int[] random;

    /** The generator must keep this song in the set at this position. */
    public boolean lockSong;
    /** The generator must keep the used part. */
    public boolean lockRange;

    /** Local file of the chart; resolved from the song database when missing. Not exported. */
    public transient String localPath = "";

    public MixTrack() {
    }

    public long resolvedOut(long chartEnd) {
        long end = outMs >= 0 ? outMs : chartEnd;
        return Math.max(Math.max(0L, inMs), end);
    }

    public String displayTitle() {
        String full = subtitle == null || subtitle.isBlank() ? title : title + " " + subtitle;
        return full == null || full.isBlank() ? (sha256.length() > 12 ? sha256.substring(0, 12) : sha256) : full;
    }

    public MixTrack copy() {
        MixTrack copy = new MixTrack();
        copy.sha256 = sha256;
        copy.md5 = md5;
        copy.title = title;
        copy.subtitle = subtitle;
        copy.artist = artist;
        copy.genre = genre;
        copy.bpm = bpm;
        copy.level = level;
        copy.inMs = inMs;
        copy.outMs = outMs;
        copy.gainDb = gainDb;
        copy.random = random == null ? null : random.clone();
        copy.lockSong = lockSong;
        copy.lockRange = lockRange;
        copy.localPath = localPath;
        return copy;
    }
}
