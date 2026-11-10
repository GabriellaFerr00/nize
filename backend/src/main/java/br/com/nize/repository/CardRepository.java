package br.com.nize.repository;

import br.com.nize.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByUserIdOrderByIdAsc(Long userId);

    List<Card> findByUserIdAndActiveOrderByIdAsc(Long userId, boolean active);

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
