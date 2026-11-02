package br.com.nize.service;

public class CardNameTakenException extends RuntimeException {

    public CardNameTakenException() {
        super("card name already used in this year");
    }
}
