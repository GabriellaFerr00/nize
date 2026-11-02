package br.com.nize.api;

import br.com.nize.domain.Person;

public record PersonResponse(Long id, int year, String name, boolean active) {

    static PersonResponse from(Person person) {
        return new PersonResponse(person.getId(), person.getYearNumber(), person.getName(), person.isActive());
    }
}
