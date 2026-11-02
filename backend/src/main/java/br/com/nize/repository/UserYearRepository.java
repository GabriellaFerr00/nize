package br.com.nize.repository;

import br.com.nize.domain.UserYear;
import br.com.nize.domain.UserYearId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface UserYearRepository extends JpaRepository<UserYear, UserYearId> {

    @Query("select y.id.yearNumber from UserYear y where y.id.userId = :userId order by y.id.yearNumber")
    List<Integer> findYearNumbers(@Param("userId") Long userId);

    /** The closest year of the user strictly before {@code year}. */
    @Query("select max(y.id.yearNumber) from UserYear y where y.id.userId = :userId and y.id.yearNumber < :year")
    Optional<Integer> findClosestEarlierYear(@Param("userId") Long userId, @Param("year") int year);
}
