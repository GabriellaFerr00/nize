package br.com.nize.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expenses by category with the share of the total (REQ-035). Pure rule: sums are whole cents; the percentage is the
 * only non-integer value, computed in exact decimal arithmetic (never floating point) with 2 places, HALF_UP. It is
 * for display only and never summed.
 */
public final class CategorySummary {

    private CategorySummary() {
    }

    public record Item(Long categoryId, long amountCents) {
        public Item {
            Objects.requireNonNull(categoryId, "categoryId");
            if (amountCents <= 0) {
                throw new IllegalArgumentException("amount must be positive: " + amountCents);
            }
        }
    }

    /** {@code percent} of the month's total expenses, e.g. 23.86. */
    public record Entry(Long categoryId, long amountCents, BigDecimal percent) {
    }

    /** One entry per category that has expenses, from the largest amount; the amounts add up to the total. */
    public static List<Entry> of(List<Item> items) {
        Map<Long, Long> byCategory = new LinkedHashMap<>();
        long total = 0;
        for (Item item : items) {
            byCategory.merge(item.categoryId(), item.amountCents(), Math::addExact);
            total = Math.addExact(total, item.amountCents());
        }
        long grandTotal = total;
        return byCategory.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Entry(e.getKey(), e.getValue(), percent(e.getValue(), grandTotal)))
                .toList();
    }

    /** {@code part} as a percentage of {@code whole}, 2 places, HALF_UP; 0.00 when the whole is zero. */
    public static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }
}
