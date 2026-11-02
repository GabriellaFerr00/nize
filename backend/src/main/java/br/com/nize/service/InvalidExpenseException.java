package br.com.nize.service;

/** A rejected expense; {@code code} is the stable error code the API returns (400). */
public class InvalidExpenseException extends RuntimeException {

    private final String code;

    public InvalidExpenseException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
