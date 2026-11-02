package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.repository.CardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Card rules. The user id always comes from the token (D-6); other users' rows are "not found". */
@Service
public class CardService {

    private final CardRepository cards;
    private final YearGuard yearGuard;

    public CardService(CardRepository cards, YearGuard yearGuard) {
        this.cards = cards;
        this.yearGuard = yearGuard;
    }

    @Transactional(readOnly = true)
    public List<Card> list(Long userId, int year, boolean activeOnly) {
        yearGuard.requireYear(userId, year);
        return activeOnly
                ? cards.findByUserIdAndYearNumberAndActiveOrderByIdAsc(userId, year, true)
                : cards.findByUserIdAndYearNumberOrderByIdAsc(userId, year);
    }

    @Transactional
    public Card create(Long userId, int year, String name, int dueDay) {
        yearGuard.requireYear(userId, year);
        if (cards.existsByUserIdAndYearNumberAndNameIgnoreCase(userId, year, name)) {
            throw new CardNameTakenException();
        }
        return cards.save(new Card(userId, year, name, dueDay));
    }

    @Transactional
    public Card update(Long userId, Long id, String name, int dueDay, boolean active) {
        Card card = cards.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (cards.existsByUserIdAndYearNumberAndNameIgnoreCaseAndIdNot(userId, card.getYearNumber(), name, card.getId())) {
            throw new CardNameTakenException();
        }
        card.update(name, dueDay, active);
        return card;
    }
}
