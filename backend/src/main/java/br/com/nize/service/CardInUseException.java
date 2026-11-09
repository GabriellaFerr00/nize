package br.com.nize.service;

/** A card that expenses still use is kept (REQ-013); the count goes back to the user. */
public class CardInUseException extends RuntimeException {

    private final long expenseCount;

    public CardInUseException(long expenseCount) {
        super("card has " + expenseCount + " expenses");
        this.expenseCount = expenseCount;
    }

    public long getExpenseCount() {
        return expenseCount;
    }
}
