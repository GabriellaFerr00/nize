package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.YearService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class YearController {

    private final YearService service;

    public YearController(YearService service) {
        this.service = service;
    }

    @GetMapping("/api/years")
    public List<YearResponse> list() {
        return service.list(CurrentUser.id()).stream().map(y -> new YearResponse(y.year(), y.current())).toList();
    }

    @PostMapping("/api/years/{year}")
    @ResponseStatus(HttpStatus.CREATED)
    public YearCreatedResponse create(@PathVariable int year) {
        return new YearCreatedResponse(service.create(CurrentUser.id(), year));
    }
}
