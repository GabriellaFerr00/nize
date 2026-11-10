package br.com.nize.api;

import br.com.nize.domain.Card;

/**
 * {@code hasLogo}: the card has the user's own image at {@code GET /api/cards/{id}/logo}; {@code brand}: a key of
 * {@link br.com.nize.domain.CardBrand}, null when none (REQ-052).
 */
public record CardResponse(Long id, int year, String name, int dueDay, boolean active, boolean hasLogo,
                           String brand) {

    static CardResponse from(Card card, boolean hasLogo) {
        return new CardResponse(card.getId(), card.getYearNumber(), card.getName(), card.getDueDay(), card.isActive(),
                hasLogo, card.getBrand());
    }
}
