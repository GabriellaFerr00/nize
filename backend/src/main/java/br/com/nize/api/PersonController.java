package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.PersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PersonController {

    private final PersonService service;

    public PersonController(PersonService service) {
        this.service = service;
    }

    /** {@code ?active=true} returns only the people offered in the entry form. */
    @GetMapping("/api/years/{year}/people")
    public List<PersonResponse> list(@PathVariable int year, @RequestParam(defaultValue = "false") boolean active) {
        return service.list(CurrentUser.id(), year, active).stream().map(PersonResponse::from).toList();
    }

    @PostMapping("/api/years/{year}/people")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse create(@PathVariable int year, @Valid @RequestBody CreatePersonRequest request) {
        return PersonResponse.from(service.create(CurrentUser.id(), year, request.name(), request.color()));
    }

    @PutMapping("/api/people/{id}")
    public PersonResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePersonRequest request) {
        return PersonResponse.from(service.update(CurrentUser.id(), id, request.name(), request.active(), request.color()));
    }
}
