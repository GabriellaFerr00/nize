package br.com.nize.repository;

import br.com.nize.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByUserIdAndYearNumberOrderByIdAsc(Long userId, int yearNumber);

    List<Card> findByUserIdAndYearNumberAndActiveOrderByIdAsc(Long userId, int yearNumber, boolean active);

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndYearNumberAndNameIgnoreCase(Long userId, int yearNumber, String name);

    boolean existsByUserIdAndYearNumberAndNameIgnoreCaseAndIdNot(Long userId, int yearNumber, String name, Long id);

    Optional<Card> findByUserIdAndYearNumberAndNameIgnoreCase(Long userId, int yearNumber, String name);
}
