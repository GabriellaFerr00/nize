package br.com.nize.repository;

import br.com.nize.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByUserIdAndYearNumber(Long userId, int yearNumber);

    List<Category> findByUserIdAndYearNumberOrderByIdAsc(Long userId, int yearNumber);

    List<Category> findByUserIdAndYearNumberAndActiveOrderByIdAsc(Long userId, int yearNumber, boolean active);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndYearNumberAndNameIgnoreCase(Long userId, int yearNumber, String name);

    boolean existsByUserIdAndYearNumberAndNameIgnoreCaseAndIdNot(Long userId, int yearNumber, String name, Long id);

    Optional<Category> findByUserIdAndYearNumberAndNameIgnoreCase(Long userId, int yearNumber, String name);
}
