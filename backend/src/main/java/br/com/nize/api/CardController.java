package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.CardService;
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
public class CardController {

    private final CardService service;

    public CardController(CardService service) {
        this.service = service;
    }

    /** {@code ?active=true} returns only the cards offered in the entry form. */
    @GetMapping("/api/years/{year}/cards")
    public List<CardResponse> list(@PathVariable int year, @RequestParam(defaultValue = "false") boolean active) {
        return service.list(CurrentUser.id(), year, active).stream().map(CardResponse::from).toList();
    }

    @PostMapping("/api/years/{year}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@PathVariable int year, @Valid @RequestBody CreateCardRequest request) {
        return CardResponse.from(service.create(CurrentUser.id(), year, request.name(), request.dueDay()));
    }

    @PutMapping("/api/cards/{id}")
    public CardResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCardRequest request) {
        return CardResponse.from(service.update(
                CurrentUser.id(), id, request.name(), request.dueDay(), request.active()));
    }
}
