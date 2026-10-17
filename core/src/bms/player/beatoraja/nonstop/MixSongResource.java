package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.song.SongResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Virtual chart location of a compiled mix. Sound and image names in the
 * merged chart carry a {@code nsmix/<track>/} prefix; resolving such a name
 * returns the real resource in that song's own folder (or archive), so the
 * existing audio and BGA loaders work unchanged.
 */
public final class MixSongResource implements SongResource {
    public static final String PREFIX = "nsmix/";

    private final String setId;
    private final SongResource[] directories;
    private final boolean directory;

    private MixSongResource(String setId, SongResource[] directories, boolean directory) {
        this.setId = setId;
        this.directories = directories;
        this.directory = directory;
    }

    /** @param charts each track's chart resource, in track order */
    public static MixSongResource chart(String setId, List<SongResource> charts) {
        SongResource[] parents = new SongResource[charts.size()];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = charts.get(i).parent();
        }
        return new MixSongResource(setId, parents, false);
    }

    public static String prefixed(int track, String name) {
        return PREFIX + track + "/" + (name == null ? "" : name);
    }

    @Override
    public SongResource parent() {
        return directory ? this : new MixSongResource(setId, directories, true);
    }

    @Override
    public SongResource resolve(String relativePath) {
        String path = relativePath == null ? "" : relativePath.replace('\\', '/');
        if (path.startsWith(PREFIX)) {
            int slash = path.indexOf('/', PREFIX.length());
            if (slash > PREFIX.length()) {
                try {
                    int track = Integer.parseInt(path.substring(PREFIX.length(), slash));
                    if (track >= 0 && track < directories.length) {
                        return directories[track].resolve(relativePath.substring(slash + 1));
                    }
                } catch (NumberFormatException ignored) {
                    // falls through to an unresolvable name
                }
            }
        }
        return new Missing(this, path);
    }

    @Override
    public String name() {
        return directory ? "nonstop-mix" : "nonstop-mix.mix";
    }

    @Override
    public String displayPath() {
        return "nonstop-mix/" + setId + (directory ? "" : "/nonstop-mix.mix");
    }

    @Override
    public String cacheKey() {
        return "nonstop-mix:" + setId + (directory ? "/" : "");
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public long size() {
        return 0;
    }

    @Override
    public InputStream openStream() throws IOException {
        throw new IOException("a compiled NONSTOP MIX has no chart file");
    }

    @Override
    public List<SongResource> list() {
        return List.of();
    }

    /** A name that does not belong to any track; loaders treat it as absent. */
    private record Missing(SongResource parent, String name) implements SongResource {
        @Override
        public SongResource resolve(String relativePath) {
            return new Missing(this, name + "/" + relativePath);
        }

        @Override
        public String displayPath() {
            return "nonstop-mix-missing/" + name;
        }

        @Override
        public String cacheKey() {
            return displayPath();
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public long size() {
            return 0;
        }

        @Override
        public InputStream openStream() throws IOException {
            throw new IOException("missing: " + name);
        }

        @Override
        public List<SongResource> list() {
            return List.of();
        }
    }
}
