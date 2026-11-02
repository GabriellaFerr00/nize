package br.com.nize.service;

/**
 * Contract between categories and expenses (REQ-012): how many expenses use a category.
 * Phase 3 (TASK-023) replaces {@link NoExpensesYet} with a real implementation over the expense table.
 */
public interface CategoryUsage {

    long countExpenses(Long userId, Long categoryId);
}
