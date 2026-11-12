package br.com.nize.repository;

import br.com.nize.domain.Income;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface IncomeRepository extends JpaRepository<Income, Long> {

    Optional<Income> findByIdAndUserId(Long id, Long userId);

    List<Income> findByUserIdAndMonthOrderByIdAsc(Long userId, String month);

    List<Income> findByUserIdAndMonthIn(Long userId, Collection<String> months);

    List<Income> findByUserIdAndInstallmentGroupIdIn(Long userId, Collection<String> groups);

    /** A fixed income and its copies in other months (REQ-054). */
    List<Income> findByUserIdAndRecurrenceId(Long userId, String recurrenceId);
}
