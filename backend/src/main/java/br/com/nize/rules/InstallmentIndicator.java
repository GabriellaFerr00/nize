package br.com.nize.rules;

/** The "N/M" label shown next to an installment, e.g. "7/12" (REQ-021). Pure rule. */
public final class InstallmentIndicator {

    private InstallmentIndicator() {
    }

    /** Both numbers null means "no installments" and gives an empty label; otherwise 1 <= current <= total. */
    public static String format(Integer current, Integer total) {
        if (current == null && total == null) {
            return "";
        }
        if (current == null || total == null) {
            throw new IllegalArgumentException("current and total must be both set or both empty");
        }
        if (current < 1 || current > total) {
            throw new IllegalArgumentException("invalid installment " + current + "/" + total);
        }
        return current + "/" + total;
    }
}
