package br.com.nize.service;

public class CategoryInUseException extends RuntimeException {

    private final long expenseCount;

    public CategoryInUseException(long expenseCount) {
        super("category has " + expenseCount + " expenses");
        this.expenseCount = expenseCount;
    }

    public long getExpenseCount() {
        return expenseCount;
    }
}
