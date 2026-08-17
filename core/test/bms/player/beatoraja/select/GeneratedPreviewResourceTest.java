package bms.player.beatoraja.select;

import bms.player.beatoraja.audio.GeneratedPreviewRenderer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedPreviewResourceTest {

    @Test
    void wrapsRenderedPcmInAValidWaveResource() throws Exception {
        ByteBuffer pcm = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        pcm.putShort((short) 123).putShort((short) -456).flip();
        GeneratedPreviewResource resource = GeneratedPreviewResource.from(
                "cache-key",
                new GeneratedPreviewRenderer.RenderResult(pcm, 8_000, 1, 250));

        byte[] wave = resource.openStream().readAllBytes();
        assertEquals("RIFF", new String(wave, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wave, 8, 4, StandardCharsets.US_ASCII));
        assertEquals("fmt ", new String(wave, 12, 4, StandardCharsets.US_ASCII));
        assertEquals("data", new String(wave, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(8_000, ByteBuffer.wrap(wave, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertArrayEquals(new byte[]{123, 0, 56, -2}, java.util.Arrays.copyOfRange(wave, 44, 48));
        assertEquals(250, resource.durationMs());
        assertEquals("cache-key", resource.cacheKey());
    }
}
