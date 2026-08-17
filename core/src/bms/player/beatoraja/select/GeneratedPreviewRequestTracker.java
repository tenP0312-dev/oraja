package bms.player.beatoraja.select;

import bms.player.beatoraja.song.SongData;

import java.util.concurrent.atomic.AtomicLong;

/** Identifies the only generated-preview request whose result may be played. */
final class GeneratedPreviewRequestTracker {

    private final AtomicLong version = new AtomicLong();
    private volatile SongData current;

    synchronized long select(SongData song) {
        current = song;
        return version.incrementAndGet();
    }

    synchronized void clear() {
        current = null;
        version.incrementAndGet();
    }

    boolean isCurrent(SongData song, long expectedVersion) {
        return current == song && version.get() == expectedVersion;
    }

    SongData current() {
        return current;
    }
}
