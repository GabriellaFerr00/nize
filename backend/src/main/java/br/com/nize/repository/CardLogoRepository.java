package br.com.nize.repository;

import br.com.nize.domain.CardLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Every query carries the user id (NFR-004). */
public interface CardLogoRepository extends JpaRepository<CardLogo, Long> {

    Optional<CardLogo> findByCardIdAndUserId(Long cardId, Long userId);

    @Query("select l.cardId from CardLogo l where l.userId = :userId and l.cardId in :cardIds")
    Set<Long> findCardIdsWithLogo(@Param("userId") Long userId, @Param("cardIds") Collection<Long> cardIds);
}
