package br.com.nize.service;

import org.springframework.stereotype.Component;

/** Placeholder until expenses exist (Phase 3): no category is ever in use. Remove when replaced. */
@Component
class NoExpensesYet implements CategoryUsage {

    @Override
    public long countExpenses(Long userId, Long categoryId) {
        return 0;
    }
}
