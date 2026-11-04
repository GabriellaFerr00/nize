package br.com.nize.rules;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expenses by payment method and credit expenses by card, with "Sem cartão" (null card) as a group of its own, so no
 * credit expense disappears from the card summary (REQ-036, REQ-037). Pure rule: whole cents only.
 */
public final class PaymentSummary {

    private PaymentSummary() {
    }

    public enum Payment {
        PIX, CREDIT
    }

    /** {@code cardId} only with credit; null credit is "Sem cartão". */
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

    /** {@code cardId} null is "Sem cartão". */
    public record CardEntry(Long cardId, long amountCents) {
    }

    /** pix + credit = total expenses; Σ byCard = credit. Cards from the largest amount, "Sem cartão" last. */
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
                byCard.merge(item.cardId(), item.amountCents(), Math::addExact);
            }
        }
        List<CardEntry> cards = byCard.entrySet().stream()
                .map(e -> new CardEntry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing((CardEntry c) -> c.cardId() == null)
                        .thenComparing(CardEntry::amountCents, Comparator.reverseOrder())
                        .thenComparing(CardEntry::cardId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new Summary(pix, credit, cards);
    }
}
