package br.com.nize.api;

import br.com.nize.domain.Card;

/** {@code brand}: a key of {@link br.com.nize.domain.CardBrand}, null when none (REQ-052). */
public record CardResponse(Long id, String name, int dueDay, boolean active, String brand) {

    static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getName(), card.getDueDay(), card.isActive(),
                card.getBrand());
    }
}
