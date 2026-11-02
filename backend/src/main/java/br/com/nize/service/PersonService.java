package br.com.nize.service;

import br.com.nize.domain.Person;
import br.com.nize.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Person rules. The user id always comes from the token (D-6); other users' rows are "not found". */
@Service
public class PersonService {

    private final PersonRepository people;
    private final YearGuard yearGuard;

    public PersonService(PersonRepository people, YearGuard yearGuard) {
        this.people = people;
        this.yearGuard = yearGuard;
    }

    @Transactional(readOnly = true)
    public List<Person> list(Long userId, int year, boolean activeOnly) {
        yearGuard.requireYear(userId, year);
        return activeOnly
                ? people.findByUserIdAndYearNumberAndActiveOrderByIdAsc(userId, year, true)
                : people.findByUserIdAndYearNumberOrderByIdAsc(userId, year);
    }

    @Transactional
    public Person create(Long userId, int year, String name) {
        yearGuard.requireYear(userId, year);
        if (people.existsByUserIdAndYearNumberAndNameIgnoreCase(userId, year, name)) {
            throw new PersonNameTakenException();
        }
        return people.save(new Person(userId, year, name));
    }

    @Transactional
    public Person update(Long userId, Long id, String name, boolean active) {
        Person person = people.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (people.existsByUserIdAndYearNumberAndNameIgnoreCaseAndIdNot(userId, person.getYearNumber(), name, person.getId())) {
            throw new PersonNameTakenException();
        }
        person.update(name, active);
        return person;
    }
}
