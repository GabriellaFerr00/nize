package br.com.nize.repository;

import br.com.nize.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries the user id (NFR-004). */
public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByUserIdOrderByIdAsc(Long userId);

    List<Person> findByUserIdAndActiveOrderByIdAsc(Long userId, boolean active);

    Optional<Person> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
