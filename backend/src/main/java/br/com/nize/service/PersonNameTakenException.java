package br.com.nize.service;

public class PersonNameTakenException extends RuntimeException {

    public PersonNameTakenException() {
        super("person name already used in this year");
    }
}
