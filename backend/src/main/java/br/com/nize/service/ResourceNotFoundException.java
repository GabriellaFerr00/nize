package br.com.nize.service;

/** The resource does not exist or belongs to someone else: both look the same (404, D-6). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("not found");
    }
}
