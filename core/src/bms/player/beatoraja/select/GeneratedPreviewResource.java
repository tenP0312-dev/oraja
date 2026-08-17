package bms.player.beatoraja.select;

import bms.player.beatoraja.audio.GeneratedPreviewRenderer;
import bms.player.beatoraja.song.SongResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** In-memory PCM WAV used only by the Music Select preview audio path. */
final class GeneratedPreviewResource implements SongResource {

    private static final int WAV_HEADER_BYTES = 44;

    private final String cacheKey;
    private final byte[] wavBytes;
    private final long durationMs;

    static GeneratedPreviewResource from(
            String cacheKey,
            GeneratedPreviewRenderer.RenderResult rendered) {
        ByteBuffer pcm = rendered.pcmData().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        pcm.rewind();
        byte[] wav = new byte[WAV_HEADER_BYTES + pcm.remaining()];
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pcmBytes = pcm.remaining();
        header.putInt(0x46464952); // RIFF
        header.putInt(36 + pcmBytes);
        header.putInt(0x45564157); // WAVE
        header.putInt(0x20746d66); // fmt
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) rendered.channels());
        header.putInt(rendered.sampleRate());
        int byteRate = rendered.sampleRate() * rendered.channels() * 2;
        header.putInt(byteRate);
        header.putShort((short) (rendered.channels() * 2));
        header.putShort((short) 16);
        header.putInt(0x61746164); // data
        header.putInt(pcmBytes);
        pcm.get(wav, WAV_HEADER_BYTES, pcmBytes);
        return new GeneratedPreviewResource(cacheKey, wav, rendered.durationMs());
    }

    private GeneratedPreviewResource(String cacheKey, byte[] wavBytes, long durationMs) {
        this.cacheKey = cacheKey;
        this.wavBytes = wavBytes;
        this.durationMs = durationMs;
    }

    long durationMs() {
        return durationMs;
    }

    @Override
    public SongResource parent() {
        return this;
    }

    @Override
    public SongResource resolve(String relativePath) {
        return this;
    }

    @Override
    public String name() {
        return "generated-preview.wav";
    }

    @Override
    public String displayPath() {
        return "memory:" + cacheKey;
    }

    @Override
    public String cacheKey() {
        return cacheKey;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public long size() {
        return wavBytes.length;
    }

    @Override
    public InputStream openStream() {
        return new ByteArrayInputStream(wavBytes);
    }

    @Override
    public List<SongResource> list() {
        return List.of();
    }
}
