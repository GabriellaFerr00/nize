package br.com.nize.rules;

import java.math.BigInteger;

/**
 * Optional monthly limit of a category (REQ-051). Pure rule, whole cents only: it says how the month's spending of a
 * category stands against its limit; it never blocks anything.
 */
public final class CategoryLimit {

    /** From this share of the limit on, the category is "near" it. */
    public static final int NEAR_PERCENT = 80;

    public enum Status {
        /** No limit set. */
        NONE,
        /** Below {@value #NEAR_PERCENT}% of the limit. */
        UNDER,
        /** From {@value #NEAR_PERCENT}% up to the limit itself. */
        NEAR,
        /** Above the limit. */
        OVER
    }

    private CategoryLimit() {
    }

    public static Status of(long spentCents, Long limitCents) {
        if (spentCents < 0) {
            throw new IllegalArgumentException("spent must not be negative: " + spentCents);
        }
        if (limitCents == null) {
            return Status.NONE;
        }
        if (limitCents <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limitCents);
        }
        if (spentCents > limitCents) {
            return Status.OVER;
        }
        // spent × 100 ≥ limit × 80, in exact integers (no rounding, no overflow)
        BigInteger spent = BigInteger.valueOf(spentCents).multiply(BigInteger.valueOf(100));
        BigInteger near = BigInteger.valueOf(limitCents).multiply(BigInteger.valueOf(NEAR_PERCENT));
        return spent.compareTo(near) >= 0 ? Status.NEAR : Status.UNDER;
    }
}
