package br.com.nize.service;

/** What deleting an installment removes (REQ-050): only that one, or that one and the following ones. */
public enum DeleteScope {
    THIS, FOLLOWING;

    /** {@code null} or "this" → only that one; "following" → that one and the next ones; anything else → 400. */
    public static DeleteScope parse(String value) {
        if (value == null || value.equals("this")) {
            return THIS;
        }
        if (value.equals("following")) {
            return FOLLOWING;
        }
        throw new InvalidExpenseException("VALIDATION_ERROR");
    }
}
