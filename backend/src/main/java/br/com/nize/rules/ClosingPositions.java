package br.com.nize.rules;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * What is left (or missing) on each closing day of the month (REQ-030, REQ-031, REQ-032, REQ-048). Pure rule: no
 * Spring, no JPA, no floating point. A closing day past the end of the month counts as the last day, so the default
 * days 15 and 31 are "the 15th" and "the last day" in every month.
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

    public record Income(long amountCents, LocalDate date) {
        public Income {
            requirePositive(amountCents);
            Objects.requireNonNull(date, "date");
        }
    }

    /** {@code balanceCents} is income minus expenses up to {@code day} (inclusive) and may be negative. */
    public record Position(int day, long balanceCents) {
    }

    /** Pix: the payment date. Credit with a card: the card's due day in this month. Credit without a card: the date. */
    public static LocalDate effectiveDate(Expense expense, YearMonth month) {
        if (expense.payment() == Payment.CREDIT && expense.cardDueDay() != null) {
            return month.atDay(Math.min(expense.cardDueDay(), month.lengthOfMonth()));
        }
        return expense.date();
    }

    /**
     * One position per closing day, in ascending order. Days that land on the same date (30 and 31 in February) give
     * one position. No closing days, no positions. Overflowing sums throw {@link ArithmeticException}.
     */
    public static List<Position> of(YearMonth month, List<Expense> expenses, List<Income> incomes,
                                    Collection<Integer> closingDays) {
        TreeSet<Integer> limits = new TreeSet<>();
        for (int day : closingDays) {
            limits.add(Math.min(requireDay(day), month.lengthOfMonth()));
        }

        // Net amount per day of the month, then a running sum: each position is the sum up to its day.
        long[] netByDay = new long[month.lengthOfMonth() + 1];
        for (Income income : incomes) {
            int day = dayInMonth(income.date(), month);
            netByDay[day] = Math.addExact(netByDay[day], income.amountCents());
        }
        for (Expense expense : expenses) {
            dayInMonth(expense.date(), month);
            int day = effectiveDate(expense, month).getDayOfMonth();
            netByDay[day] = Math.subtractExact(netByDay[day], expense.amountCents());
        }

        List<Position> positions = new ArrayList<>(limits.size());
        long running = 0;
        int summedUpTo = 0;
        for (int limit : limits) {
            for (int day = summedUpTo + 1; day <= limit; day++) {
                running = Math.addExact(running, netByDay[day]);
            }
            summedUpTo = limit;
            positions.add(new Position(limit, running));
        }
        return List.copyOf(positions);
    }

    private static int dayInMonth(LocalDate date, YearMonth month) {
        if (!YearMonth.from(date).equals(month)) {
            throw new IllegalArgumentException("date " + date + " is outside " + month);
        }
        return date.getDayOfMonth();
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
