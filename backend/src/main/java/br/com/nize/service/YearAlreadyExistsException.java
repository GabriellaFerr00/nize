package br.com.nize.service;

public class YearAlreadyExistsException extends RuntimeException {

    public YearAlreadyExistsException(int year) {
        super("year already exists: " + year);
    }
}
