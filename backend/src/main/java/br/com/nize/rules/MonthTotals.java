package br.com.nize.rules;

import java.util.List;
import java.util.Objects;

/**
 * Month totals in cents (REQ-027, REQ-028, REQ-029). Pure rule: no Spring, no JPA, no floating point.
 * Nothing here is ever stored; the API computes it on every read (NFR-002).
 */
public final class MonthTotals {

    private MonthTotals() {
    }

    /** Which list an expense belongs to. A fixed expense paid by credit card is still {@link #FIXED}. */
    public enum Kind {
        FIXED, CARD
    }

    public record Expense(Kind kind, long amountCents) {
        public Expense {
            Objects.requireNonNull(kind, "kind");
            requirePositive(amountCents);
        }
    }

    /** {@code balanceCents} is income minus expenses and may be negative. */
    public record Totals(long totalExpensesCents, long totalFixedCents, long totalIncomeCents, long balanceCents) {
    }

    /** Overflowing sums throw {@link ArithmeticException} instead of wrapping around. */
    public static Totals of(List<Expense> expenses, List<Long> incomeCents) {
        long totalExpenses = 0;
        long totalFixed = 0;
        for (Expense expense : expenses) {
            totalExpenses = Math.addExact(totalExpenses, expense.amountCents());
            if (expense.kind() == Kind.FIXED) {
                totalFixed = Math.addExact(totalFixed, expense.amountCents());
            }
        }
        long totalIncome = 0;
        for (long income : incomeCents) {
            totalIncome = Math.addExact(totalIncome, requirePositive(income));
        }
        return new Totals(totalExpenses, totalFixed, totalIncome, Math.subtractExact(totalIncome, totalExpenses));
    }

    private static long requirePositive(long cents) {
        if (cents <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + cents);
        }
        return cents;
    }
}
