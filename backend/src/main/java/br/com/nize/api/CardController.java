package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.CardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @GetMapping("/api/cards")
    public List<CardResponse> list(@RequestParam(defaultValue = "false") boolean active) {
        return service.list(CurrentUser.id(), active).stream().map(CardResponse::from).toList();
    }

    @PostMapping("/api/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@Valid @RequestBody CreateCardRequest request) {
        return CardResponse.from(service.create(CurrentUser.id(), request.name(), request.dueDay(), request.brand()));
    }

    @PutMapping("/api/cards/{id}")
    public CardResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCardRequest request) {
        return CardResponse.from(service.update(CurrentUser.id(), id, request.name(), request.dueDay(), request.active(),
                request.brand()));
    }

    /** 204 when deleted; 409 {@code CARD_IN_USE} with the expense count when expenses still use it (REQ-013). */
    @DeleteMapping("/api/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }
}
