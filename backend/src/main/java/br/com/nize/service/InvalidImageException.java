package br.com.nize.service;

/** A refused card logo (REQ-052): {@code INVALID_IMAGE} or {@code IMAGE_TOO_LARGE} (400). */
public class InvalidImageException extends RuntimeException {

    private final String code;

    public InvalidImageException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
