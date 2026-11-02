package br.com.nize.service;

public class CategoryNameTakenException extends RuntimeException {

    public CategoryNameTakenException() {
        super("category name already used in this year");
    }
}
