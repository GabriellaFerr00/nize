package br.com.nize.api;

import br.com.nize.domain.Card;
import br.com.nize.domain.CardLogo;
import br.com.nize.security.CurrentUser;
import br.com.nize.service.CardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@RestController
public class CardController {

    private final CardService service;

    public CardController(CardService service) {
        this.service = service;
    }

    /** {@code ?active=true} returns only the cards offered in the entry form. */
    @GetMapping("/api/years/{year}/cards")
    public List<CardResponse> list(@PathVariable int year, @RequestParam(defaultValue = "false") boolean active) {
        List<Card> list = service.list(CurrentUser.id(), year, active);
        Set<Long> withLogo = service.cardsWithLogo(CurrentUser.id(), list.stream().map(Card::getId).toList());
        return list.stream().map(c -> CardResponse.from(c, withLogo.contains(c.getId()))).toList();
    }

    @PostMapping("/api/years/{year}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@PathVariable int year, @Valid @RequestBody CreateCardRequest request) {
        return CardResponse.from(service.create(CurrentUser.id(), year, request.name(), request.dueDay()), false);
    }

    @PutMapping("/api/cards/{id}")
    public CardResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCardRequest request) {
        Card card = service.update(CurrentUser.id(), id, request.name(), request.dueDay(), request.active());
        return CardResponse.from(card, !service.cardsWithLogo(CurrentUser.id(), List.of(card.getId())).isEmpty());
    }

    /** 204 when deleted; 409 {@code CARD_IN_USE} with the expense count when expenses still use it (REQ-013). */
    @DeleteMapping("/api/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }

    /**
     * The user's own image for the card (REQ-052, D-21): the body is the image bytes. Only PNG, JPEG or WebP (checked
     * by the first bytes), up to 200 KB; reading stops right after the limit, so a huge body is never loaded.
     */
    @PutMapping("/api/cards/{id}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void putLogo(@PathVariable Long id, HttpServletRequest request) throws IOException {
        byte[] data;
        try (InputStream in = request.getInputStream()) {
            data = in.readNBytes(CardService.MAX_LOGO_BYTES + 1);
        }
        service.setLogo(CurrentUser.id(), id, data);
    }

    /** The stored image with its own type; never sniffed, never cached by shared caches. */
    @GetMapping("/api/cards/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        CardLogo logo = service.logo(CurrentUser.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getContentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(logo.getData());
    }

    @DeleteMapping("/api/cards/{id}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLogo(@PathVariable Long id) {
        service.deleteLogo(CurrentUser.id(), id);
    }
}
