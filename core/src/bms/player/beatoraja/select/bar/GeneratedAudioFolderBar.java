package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.generated.AudioChartSession;
import bms.player.beatoraja.select.MusicSelector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;

/**
 * Lists the audio folder configured for generated charts (#436): sub-folders
 * first, then audio files, read straight from disk instead of the song
 * database. Entering a file opens its controller-navigable chart settings.
 */
public final class GeneratedAudioFolderBar extends DirectoryBar {
    private final Path directory;
    private final String title;

    public GeneratedAudioFolderBar(MusicSelector selector, Path directory, String title) {
        super(selector, true);
        this.directory = directory;
        this.title = title;
        setSortable(false);
    }

    /** @return the root folder bar, or {@code null} when no existing folder is configured */
    public static GeneratedAudioFolderBar root(MusicSelector selector, String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            Path directory = Path.of(configured).toAbsolutePath().normalize();
            if (!Files.isDirectory(directory)) {
                return null;
            }
            return new GeneratedAudioFolderBar(selector, directory,
                    BMSIRArenaI18n.text("音源から譜面生成", "CHARTS FROM AUDIO"));
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    @Override
    public Bar[] getChildren() {
        List<Bar> children = new ArrayList<>();
        for (Path entry : entries(directory)) {
            if (Files.isDirectory(entry)) {
                children.add(new GeneratedAudioFolderBar(selector, entry, entry.getFileName().toString()));
            } else {
                children.add(new GeneratedAudioChartBar(selector, entry));
            }
        }
        if (children.isEmpty()) {
            // an empty folder still needs one row so it can be entered and left
            children.add(new FunctionBar((currentSelector, self) -> currentSelector.getBarManager().updateBar(),
                    BMSIRArenaI18n.text("音声ファイル（MP3/OGG/WAV/FLAC）がありません",
                            "No audio files (MP3/OGG/WAV/FLAC)"), STYLE_SEARCH));
        }
        return children.toArray(new Bar[0]);
    }

    /** Visible sub-folders, then audio files, each sorted by name ignoring case. */
    static List<Path> entries(Path directory) {
        List<Path> folders = new ArrayList<>();
        List<Path> audio = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> !path.getFileName().toString().startsWith("."))
                    .forEach(path -> {
                        if (Files.isDirectory(path)) {
                            folders.add(path);
                        } else if (AudioChartSession.isAudioFile(path) && Files.isRegularFile(path)) {
                            audio.add(path);
                        }
                    });
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
        Comparator<Path> byName = Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT));
        folders.sort(byName);
        audio.sort(byName);
        folders.addAll(audio);
        return folders;
    }
}
