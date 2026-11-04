package br.com.nize.rules;

import java.util.List;

/**
 * Own balance (REQ-034): the month balance without third-party expenses and without reimbursements, i.e.
 * balance + third-party expenses − reimbursements. Pure rule: no Spring, no JPA, no floating point.
 */
public final class OwnBalance {

    private OwnBalance() {
    }

    /** Overflowing sums throw {@link ArithmeticException}. The result may be negative. */
    public static long of(long monthBalanceCents, List<Long> thirdPartyExpensesCents, List<Long> reimbursementsCents) {
        return Math.subtractExact(
                Math.addExact(monthBalanceCents, sum(thirdPartyExpensesCents)), sum(reimbursementsCents));
    }

    private static long sum(List<Long> amountsCents) {
        long total = 0;
        for (long cents : amountsCents) {
            if (cents <= 0) {
                throw new IllegalArgumentException("amount must be positive: " + cents);
            }
            total = Math.addExact(total, cents);
        }
        return total;
    }
}
