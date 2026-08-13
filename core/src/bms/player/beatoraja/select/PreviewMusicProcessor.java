package bms.player.beatoraja.select;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.Config.SongPreview;
import bms.player.beatoraja.audio.AudioDriver;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

/**
 * プレビュー再生管理用クラス
 *
 * @author exch
 */
public class PreviewMusicProcessor {
    private static final Logger logger = LoggerFactory.getLogger(PreviewMusicProcessor.class);
    /**
     * 音源読み込みタスク
     */
    private Deque<PreviewCommand> commands = new ConcurrentLinkedDeque<>();

    private PreviewThread preview;

    private String defaultMusic = "";

    private SongData current;

    private final AudioDriver audio;

    private final Config config;

    public PreviewMusicProcessor(AudioDriver audio, Config config) {
        this.audio = audio;
        this.config = config;
    }

    public void setDefault(String path) {
        defaultMusic = (path != null ? path : "");
    }

    public void start(SongData song) {
        if(preview == null) {
            preview = new PreviewThread();
            preview.start();
        }
        current = song;

        commands.add(new PreviewCommand(resolvePreview(song)));
    }

    static String resolvePreviewPath(SongData song) {
        SongResource resource = resolvePreview(song);
        return resource != null ? resource.displayPath() : "";
    }

    private static SongResource resolvePreview(SongData song) {
        if (song == null
                || song.getPath() == null
                || song.getPath().isBlank()
                || song.getPreview() == null
                || song.getPreview().isBlank()) {
            return null;
        }
        try {
            SongResource chart = SongResources.fromPath(Paths.get(song.getPath()));
            return chart.parent().resolve(song.getPreview());
        } catch (InvalidPathException e) {
            logger.warn(e.getMessage());
            return null;
        }
    }

    public SongData getSongData() {
        return current;
    }

    public void stop() {
        preview.stop = true;
        preview = null;
    }

    class PreviewThread extends Thread {

        private boolean stop;
        private SongResource playingResource;
        private String playing;
        private float currentVolume;

        public void run() {
            audio.play(defaultMusic, config.getAudioConfig().getSystemvolume(), true);
            playing = defaultMusic;
            currentVolume = config.getAudioConfig().getSystemvolume();
            while(!stop) {
                if(!commands.isEmpty()) {
                    SongResource resource = commands.removeFirst().resource();
                    String path = resource != null ? resource.cacheKey() : defaultMusic;
                    if(!path.equals(playing)) {
                        stopPreview(true);
                        if(resource != null) {
                            audio.play(resource, config.getAudioConfig().getSystemvolume(), config.getSongPreview() == SongPreview.LOOP);
                        } else {
                            audio.setVolume(defaultMusic, config.getAudioConfig().getSystemvolume());
                        }
                        playingResource = resource;
                        playing = path;
                    }
                } else if(playingResource != null && !audio.isPlaying(playingResource)){
                	// プレビュー演奏終了後に選曲BGMに戻す
                    stopPreview(true);
                    audio.setVolume(defaultMusic, config.getAudioConfig().getSystemvolume());
                    playing = defaultMusic;
                } else if(currentVolume != config.getAudioConfig().getSystemvolume()){
                    if (playingResource != null) {
                        audio.setVolume(playingResource, config.getAudioConfig().getSystemvolume());
                    } else {
                        audio.setVolume(playing, config.getAudioConfig().getSystemvolume());
                    }
                    currentVolume = config.getAudioConfig().getSystemvolume();
                } else {
                    try {
                        sleep(50);
                    } catch (InterruptedException e) {
                    }
                }
            }
            this.stopPreview(false);
        }

        private void stopPreview(boolean pause) {
            if(playing != null && playing.length() > 0) {
                if(playingResource != null) {
                    audio.stop(playingResource);
                    audio.dispose(playingResource);
                    playingResource = null;
                } else if(pause) {
                	for(int i = 10;i >= 0;i--) {
                		float vol = i * 0.1f * config.getAudioConfig().getSystemvolume();
                        audio.setVolume(playing, vol);
                        // TODO フェードアウトはAudioDriver側で実装したい
                        try {
							sleep(15);
						} catch (InterruptedException e) {
						}
                	}
                } else {
                    audio.stop(playing);
                }
            }
        }
    }

    private record PreviewCommand(SongResource resource) {
    }
}
