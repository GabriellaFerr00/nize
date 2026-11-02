package br.com.nize.service;

import br.com.nize.repository.ExpenseRepository;
import org.springframework.stereotype.Component;

/** The real CategoryUsage: how many of the user's expenses use a category (REQ-012). */
@Component
class ExpenseCategoryUsage implements CategoryUsage {

    private final ExpenseRepository expenses;

    ExpenseCategoryUsage(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    @Override
    public long countExpenses(Long userId, Long categoryId) {
        return expenses.countByUserIdAndCategoryId(userId, categoryId);
    }
}
