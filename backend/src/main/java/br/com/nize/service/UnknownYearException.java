package br.com.nize.service;

public class UnknownYearException extends RuntimeException {

    public UnknownYearException(int year) {
        super("year not available for this user: " + year);
    }
}
