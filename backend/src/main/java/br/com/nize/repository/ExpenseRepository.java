package br.com.nize.repository;

import br.com.nize.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    long countByUserIdAndCategoryId(Long userId, Long categoryId);

    long countByUserIdAndCardId(Long userId, Long cardId);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    List<Expense> findByUserIdAndMonthOrderByIdAsc(Long userId, String month);

    List<Expense> findByUserIdAndMonthIn(Long userId, Collection<String> months);

    List<Expense> findByUserIdAndInstallmentGroupIdIn(Long userId, Collection<String> groups);
}
