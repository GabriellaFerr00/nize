package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Card rules. The user id always comes from the token (D-6); other users' rows are "not found". */
@Service
public class CardService {

    private final CardRepository cards;
    private final ExpenseRepository expenses;

    public CardService(CardRepository cards, ExpenseRepository expenses) {
        this.cards = cards;
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public List<Card> list(Long userId, boolean activeOnly) {
        return activeOnly
                ? cards.findByUserIdAndActiveOrderByIdAsc(userId, true)
                : cards.findByUserIdOrderByIdAsc(userId);
    }

    @Transactional
    public Card create(Long userId, String name, int dueDay, String brand) {
        if (cards.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new CardNameTakenException();
        }
        Card card = new Card(userId, name, dueDay);
        card.setBrand(brand);
        return cards.save(card);
    }

    @Transactional
    /** Full replacement: a missing {@code brand} removes it (REQ-052). */
    public Card update(Long userId, Long id, String name, int dueDay, boolean active, String brand) {
        Card card = cards.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (cards.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, card.getId())) {
            throw new CardNameTakenException();
        }
        card.update(name, dueDay, active);
        card.setBrand(brand);
        return card;
    }

    /** Deletes right away if no expense uses it; otherwise keeps it and reports how many do (REQ-013). */
    @Transactional
    public void delete(Long userId, Long id) {
        Card card = cards.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        long used = expenses.countByUserIdAndCardId(userId, card.getId());
        if (used > 0) {
            throw new CardInUseException(used);
        }
        cards.delete(card);
    }
}
