package br.com.nize.api;

import br.com.nize.domain.Card;

/** {@code hasLogo}: the card has the user's own image at {@code GET /api/cards/{id}/logo} (REQ-052). */
public record CardResponse(Long id, int year, String name, int dueDay, boolean active, boolean hasLogo) {

    static CardResponse from(Card card, boolean hasLogo) {
        return new CardResponse(card.getId(), card.getYearNumber(), card.getName(), card.getDueDay(), card.isActive(),
                hasLogo);
    }
}
