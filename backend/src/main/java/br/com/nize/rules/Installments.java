package br.com.nize.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Installment amounts in cents. Pure rule (REQ-018, REQ-020): no Spring, no JPA, no floating point. */
public final class Installments {

    private Installments() {
    }

    /**
     * Splits a total into {@code count} installments without losing cents: every installment but the last is
     * {@code total / count} (integer division); the last takes the remainder. R$ 100,00 in 3 gives 3333, 3333, 3334.
     * The sum is always the total, and each installment is at least one cent.
     */
    public static List<Long> splitTotal(long totalCents, int count) {
        if (totalCents <= 0) {
            throw new IllegalArgumentException("total must be positive: " + totalCents);
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1: " + count);
        }
        if (totalCents < count) {
            throw new IllegalArgumentException("each installment needs at least 1 cent: " + totalCents + " in " + count);
        }
        long base = totalCents / count;
        List<Long> parts = new ArrayList<>(count);
        for (int i = 0; i < count - 1; i++) {
            parts.add(base);
        }
        parts.add(totalCents - base * (count - 1));
        return Collections.unmodifiableList(parts);
    }

    /** {@code count} installments of the same value, for when the user types the installment amount (4x of R$ 50,00). */
    public static List<Long> repeatValue(long installmentCents, int count) {
        if (installmentCents <= 0) {
            throw new IllegalArgumentException("installment must be positive: " + installmentCents);
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1: " + count);
        }
        return Collections.nCopies(count, installmentCents);
    }
}
