package br.com.nize.repository;

import br.com.nize.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByUserId(Long userId);

    List<Category> findByUserIdOrderByIdAsc(Long userId);

    List<Category> findByUserIdAndActiveOrderByIdAsc(Long userId, boolean active);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
