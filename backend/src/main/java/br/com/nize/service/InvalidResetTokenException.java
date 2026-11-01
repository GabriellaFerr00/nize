package br.com.nize.service;

/** Unknown, used and expired tokens are deliberately indistinguishable. */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("invalid reset token");
    }
}
