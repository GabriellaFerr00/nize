package br.com.nize.api;

import br.com.nize.domain.Card;

public record CardResponse(Long id, int year, String name, int dueDay, boolean active) {

    static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getYearNumber(), card.getName(), card.getDueDay(), card.isActive());
    }
}
