package br.com.nize.rules;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * What to create when a month is opened for the first time (REQ-022). Pure rule: it only decides, from plain
 * values, which previous-month items are carried over; the service does the copying (values, dates, years).
 */
public final class MonthLoad {

    public enum Action {
        /** Copy a fixed expense, with "paid" cleared. */
        COPY_FIXED,
        /** Create the next installment of a purchase in installments. */
        NEXT_INSTALLMENT
    }

    /** The facts about one previous-month expense that matter here; {@code key} is the caller's own handle. */
    public record PreviousExpense(String key, boolean fixed, String installmentGroupId,
                                  Integer installmentNumber, Integer installmentCount) {
    }

    /** An installment that already exists somewhere (group + number are unique, D-8). */
    public record InstallmentKey(String group, int number) {
    }

    /** {@code nextNumber} is set only for {@link Action#NEXT_INSTALLMENT}. */
    public record Decision(String sourceKey, Action action, Integer nextNumber) {
    }

    private MonthLoad() {
    }

    /**
     * Fixed expenses are always copied; one-off expenses never are. An installment N of M (N < M) yields N+1 unless
     * automatic installments are off, the installment already exists, or it has no group to dedupe by.
     * The decisions keep the order of {@code previousMonth}.
     */
    public static List<Decision> decide(List<PreviousExpense> previousMonth, Set<InstallmentKey> existingInstallments,
                                        boolean automaticInstallments) {
        List<Decision> decisions = new ArrayList<>();
        for (PreviousExpense expense : previousMonth) {
            if (expense.fixed()) {
                decisions.add(new Decision(expense.key(), Action.COPY_FIXED, null));
                continue;
            }
            if (!automaticInstallments || !hasNextInstallment(expense)) {
                continue;
            }
            int next = expense.installmentNumber() + 1;
            if (!existingInstallments.contains(new InstallmentKey(expense.installmentGroupId(), next))) {
                decisions.add(new Decision(expense.key(), Action.NEXT_INSTALLMENT, next));
            }
        }
        return decisions;
    }

    /**
     * The months already opened after {@code month} that do not have a copy of a fixed item yet, oldest first
     * (REQ-054): where a fixed item that was just created (or just became fixed) must be copied. Earlier months and
     * {@code month} itself never change.
     */
    public static List<String> monthsToFill(String month, Collection<String> openedMonths, Set<String> monthsWithCopy) {
        return openedMonths.stream()
                .filter(m -> isAfter(m, month) && !monthsWithCopy.contains(m))
                .sorted()
                .toList();
    }

    /** {@code month} comes after {@code reference} (both {@code AAAA-MM}). */
    public static boolean isAfter(String month, String reference) {
        return YearMonth.parse(month).isAfter(YearMonth.parse(reference));
    }

    private static boolean hasNextInstallment(PreviousExpense expense) {
        return expense.installmentGroupId() != null
                && expense.installmentNumber() != null
                && expense.installmentCount() != null
                && expense.installmentNumber() < expense.installmentCount();
    }
}
