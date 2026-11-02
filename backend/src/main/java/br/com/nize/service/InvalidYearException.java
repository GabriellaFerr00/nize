package br.com.nize.service;

public class InvalidYearException extends RuntimeException {

    public InvalidYearException(int year) {
        super("year out of range: " + year);
    }
}
