package br.com.nize.repository;

import br.com.nize.domain.CategoryMonthLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface CategoryMonthLimitRepository extends JpaRepository<CategoryMonthLimit, CategoryMonthLimit.Key> {

    List<CategoryMonthLimit> findByUserIdAndMonth(Long userId, String month);

    Optional<CategoryMonthLimit> findByUserIdAndCategoryIdAndMonth(Long userId, Long categoryId, String month);

    List<CategoryMonthLimit> findByUserIdAndCategoryId(Long userId, Long categoryId);
}
