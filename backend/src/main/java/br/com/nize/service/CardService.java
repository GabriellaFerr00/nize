package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.domain.CardLogo;
import br.com.nize.repository.CardLogoRepository;
import br.com.nize.rules.ImageFormat;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Card rules. The user id always comes from the token (D-6); other users' rows are "not found". */
@Service
public class CardService {

    private final CardRepository cards;
    private final YearGuard yearGuard;
    private final ExpenseRepository expenses;
    private final CardLogoRepository logos;

    /** Largest card logo accepted (REQ-052). */
    public static final int MAX_LOGO_BYTES = 200 * 1024;

    public CardService(CardRepository cards, YearGuard yearGuard, ExpenseRepository expenses, CardLogoRepository logos) {
        this.cards = cards;
        this.yearGuard = yearGuard;
        this.expenses = expenses;
        this.logos = logos;
    }

    @Transactional(readOnly = true)
    public List<Card> list(Long userId, int year, boolean activeOnly) {
        yearGuard.requireYear(userId, year);
        return activeOnly
                ? cards.findByUserIdAndYearNumberAndActiveOrderByIdAsc(userId, year, true)
                : cards.findByUserIdAndYearNumberOrderByIdAsc(userId, year);
    }

    @Transactional
    public Card create(Long userId, int year, String name, int dueDay, String brand) {
        yearGuard.requireYear(userId, year);
        if (cards.existsByUserIdAndYearNumberAndNameIgnoreCase(userId, year, name)) {
            throw new CardNameTakenException();
        }
        Card card = new Card(userId, year, name, dueDay);
        card.setBrand(brand);
        return cards.save(card);
    }

    @Transactional
    /** Full replacement: a missing {@code brand} removes it (REQ-052). */
    public Card update(Long userId, Long id, String name, int dueDay, boolean active, String brand) {
        Card card = cards.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (cards.existsByUserIdAndYearNumberAndNameIgnoreCaseAndIdNot(userId, card.getYearNumber(), name, card.getId())) {
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
        logos.findByCardIdAndUserId(card.getId(), userId).ifPresent(logos::delete);
        cards.delete(card);
    }

    /** Which of these cards have a logo (for {@code hasLogo} in the list). */
    @Transactional(readOnly = true)
    public Set<Long> cardsWithLogo(Long userId, Collection<Long> cardIds) {
        return cardIds.isEmpty() ? Set.of() : logos.findCardIdsWithLogo(userId, cardIds);
    }

    /**
     * Stores the user's own image for a card (REQ-052, D-21). The type comes from the first bytes; {@code data} holds
     * at most {@link #MAX_LOGO_BYTES} + 1 bytes (the caller stops reading there), so anything longer is refused.
     */
    @Transactional
    public void setLogo(Long userId, Long id, byte[] data) {
        Card card = cards.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (data.length > MAX_LOGO_BYTES) {
            throw new InvalidImageException("IMAGE_TOO_LARGE");
        }
        String type = ImageFormat.detect(data).orElseThrow(() -> new InvalidImageException("INVALID_IMAGE"));
        Optional<CardLogo> current = logos.findByCardIdAndUserId(card.getId(), userId);
        if (current.isPresent()) {
            current.get().replace(type, data);
        } else {
            logos.save(new CardLogo(card.getId(), userId, type, data));
        }
    }

    @Transactional(readOnly = true)
    public CardLogo logo(Long userId, Long id) {
        Card card = cards.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        CardLogo logo = logos.findByCardIdAndUserId(card.getId(), userId).orElseThrow(ResourceNotFoundException::new);
        logo.getData(); // load the lazy bytes inside the transaction
        return logo;
    }

    @Transactional
    public void deleteLogo(Long userId, Long id) {
        Card card = cards.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        logos.findByCardIdAndUserId(card.getId(), userId).ifPresent(logos::delete);
    }
}
