package br.com.nize.rules;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * How much was spent in each interval between closing days (REQ-030, REQ-031, REQ-032, REQ-048): only the expenses
 * count, incomes do not. Pure rule: no Spring, no JPA, no floating point. A closing day past the end of the month counts as the last day, so the default
 * days 15 and 31 give the intervals 1–15 and 16–last day in every month. Each interval counts only its own days, and
 * the last one always runs to the end of the month.
 */
public final class ClosingPositions {

    private ClosingPositions() {
    }

    public enum Payment {
        PIX, CREDIT
    }

    /** {@code cardDueDay} only for credit with a card; null credit is "Sem cartão". */
    public record Expense(long amountCents, Payment payment, LocalDate date, Integer cardDueDay) {
        public Expense {
            requirePositive(amountCents);
            Objects.requireNonNull(payment, "payment");
            Objects.requireNonNull(date, "date");
            if (cardDueDay != null) {
                if (payment != Payment.CREDIT) {
                    throw new IllegalArgumentException("a card due day needs a credit payment");
                }
                requireDay(cardDueDay);
            }
        }
    }

    /** {@code totalExpensesCents} is the sum of the expenses from {@code fromDay} to {@code toDay} (inclusive). */
    public record Position(int fromDay, int toDay, long totalExpensesCents) {
    }

    /** Pix: the payment date. Credit with a card: the card's due day in this month. Credit without a card: the date. */
    public static LocalDate effectiveDate(Expense expense, YearMonth month) {
        if (expense.payment() == Payment.CREDIT && expense.cardDueDay() != null) {
            return month.atDay(Math.min(expense.cardDueDay(), month.lengthOfMonth()));
        }
        return expense.date();
    }

    /**
     * One position per interval, in ascending order: 1 to the first closing day, then the day after each closing day to
     * the next one; the last interval ends on the last day of the month. Days that land on the same date (30 and 31 in
     * February) give one interval. No closing days, no positions. Overflowing sums throw {@link ArithmeticException}.
     */
    public static List<Position> of(YearMonth month, List<Expense> expenses, Collection<Integer> closingDays) {
        TreeSet<Integer> limits = new TreeSet<>();
        for (int day : closingDays) {
            limits.add(Math.min(requireDay(day), month.lengthOfMonth()));
        }

        // Expenses per day of the month (by effective date), then one sum per interval (never carried into the next one).
        long[] spentByDay = new long[month.lengthOfMonth() + 1];
        for (Expense expense : expenses) {
            requireInMonth(expense.date(), month);
            int day = effectiveDate(expense, month).getDayOfMonth();
            spentByDay[day] = Math.addExact(spentByDay[day], expense.amountCents());
        }

        List<Position> positions = new ArrayList<>(limits.size());
        int from = 1;
        for (int limit : limits) {
            int to = limit == limits.last() ? month.lengthOfMonth() : limit;
            long sum = 0;
            for (int day = from; day <= to; day++) {
                sum = Math.addExact(sum, spentByDay[day]);
            }
            positions.add(new Position(from, to, sum));
            from = to + 1;
        }
        return List.copyOf(positions);
    }

    private static void requireInMonth(LocalDate date, YearMonth month) {
        if (!YearMonth.from(date).equals(month)) {
            throw new IllegalArgumentException("date " + date + " is outside " + month);
        }
    }

    private static int requireDay(int day) {
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("day must be between 1 and 31: " + day);
        }
        return day;
    }

    private static void requirePositive(long cents) {
        if (cents <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + cents);
        }
    }
}
