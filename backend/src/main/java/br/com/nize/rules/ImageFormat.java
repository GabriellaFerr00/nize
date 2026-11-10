package br.com.nize.rules;

import java.util.Optional;

/**
 * Which image a card logo is (REQ-052, D-21), from its first bytes ("magic numbers"). Only raster formats a browser
 * shows safely: PNG, JPEG and WebP. SVG is refused on purpose (it may carry script). Pure rule.
 */
public final class ImageFormat {

    private static final int[] PNG = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] JPEG = {0xFF, 0xD8, 0xFF};
    private static final int[] RIFF = {'R', 'I', 'F', 'F'};
    private static final int[] WEBP = {'W', 'E', 'B', 'P'};

    private ImageFormat() {
    }

    /** The MIME type ("image/png", "image/jpeg" or "image/webp"), or empty when it is none of them. */
    public static Optional<String> detect(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }
        if (startsWith(data, 0, PNG)) {
            return Optional.of("image/png");
        }
        if (startsWith(data, 0, JPEG)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(data, 0, RIFF) && startsWith(data, 8, WEBP)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int[] signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
