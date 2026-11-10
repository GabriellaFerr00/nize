package br.com.nize.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.nize.domain.Person;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonResponse(Long id, String name, boolean active, String color) {

    static PersonResponse from(Person person) {
        return new PersonResponse(person.getId(), person.getName(), person.isActive(),
                person.getColor());
    }
}
