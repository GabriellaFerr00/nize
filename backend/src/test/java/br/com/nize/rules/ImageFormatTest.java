package br.com.nize.rules;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Card logos (REQ-052, D-21): the format comes from the first bytes, never from the name or the header. */
class ImageFormatTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] withTail(byte[] head) {
        byte[] out = Arrays.copyOf(head, head.length + 16);
        Arrays.fill(out, head.length, out.length, (byte) 7);
        return out;
    }

    @Test
    void pngJpegAndWebpAreRecognised() {
        assertThat(ImageFormat.detect(withTail(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)))).hasValue("image/png");
        assertThat(ImageFormat.detect(withTail(bytes(0xFF, 0xD8, 0xFF, 0xE0)))).hasValue("image/jpeg");
        byte[] webp = withTail("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(ImageFormat.detect(webp)).hasValue("image/webp");
    }

    @Test
    void svgGifTextAndEmptyAreRefused() {
        assertThat(ImageFormat.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect("GIF89a....".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(ImageFormat.detect("hello".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect(null)).isEmpty();
        // a RIFF that is not WebP (e.g. a WAV sound)
        assertThat(ImageFormat.detect(withTail("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1)))).isEmpty();
    }

    @Test
    void aTruncatedSignatureIsRefused() {
        assertThat(ImageFormat.detect(bytes(0x89, 'P', 'N'))).isEmpty();
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8))).isEmpty();
    }
}
