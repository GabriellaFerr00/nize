package br.com.nize.repository;

import br.com.nize.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByUserIdAndYearNumberOrderByIdAsc(Long userId, int yearNumber);

    List<Person> findByUserIdAndYearNumberAndActiveOrderByIdAsc(Long userId, int yearNumber, boolean active);

    Optional<Person> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndYearNumberAndNameIgnoreCase(Long userId, int yearNumber, String name);

    boolean existsByUserIdAndYearNumberAndNameIgnoreCaseAndIdNot(Long userId, int yearNumber, String name, Long id);

    Optional<Person> findByUserIdAndYearNumberAndNameIgnoreCase(Long userId, int yearNumber, String name);
}
