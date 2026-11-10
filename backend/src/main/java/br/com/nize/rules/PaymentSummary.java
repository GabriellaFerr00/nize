package br.com.nize.rules;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expenses by payment method and credit expenses by card (REQ-036, REQ-037). Credit always has a card since
 * 2026-11-09; an old credit row without one counts in the credit total but is not listed by card (there is no
 * "Sem cartão" group). Pure rule: whole cents only.
 */
public final class PaymentSummary {

    private PaymentSummary() {
    }

    public enum Payment {
        PIX, CREDIT
    }

    /** {@code cardId} only with credit; null only on old credit rows (kept out of the card list). */
    public record Item(Payment payment, Long cardId, long amountCents) {
        public Item {
            Objects.requireNonNull(payment, "payment");
            if (cardId != null && payment != Payment.CREDIT) {
                throw new IllegalArgumentException("a card needs a credit payment");
            }
            if (amountCents <= 0) {
                throw new IllegalArgumentException("amount must be positive: " + amountCents);
            }
        }
    }

    public record CardEntry(Long cardId, long amountCents) {
    }

    /** pix + credit = total expenses; Σ byCard = credit when every credit has a card. Cards from the largest amount. */
    public record Summary(long pixCents, long creditCents, List<CardEntry> byCard) {
    }

    public static Summary of(List<Item> items) {
        long pix = 0;
        long credit = 0;
        Map<Long, Long> byCard = new HashMap<>();
        for (Item item : items) {
            if (item.payment() == Payment.PIX) {
                pix = Math.addExact(pix, item.amountCents());
            } else {
                credit = Math.addExact(credit, item.amountCents());
                if (item.cardId() != null) {
                    byCard.merge(item.cardId(), item.amountCents(), Math::addExact);
                }
            }
        }
        List<CardEntry> cards = byCard.entrySet().stream()
                .map(e -> new CardEntry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CardEntry::amountCents, Comparator.reverseOrder())
                        .thenComparing(CardEntry::cardId))
                .toList();
        return new Summary(pix, credit, cards);
    }
}
