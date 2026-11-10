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

    public PersonService(PersonRepository people) {
        this.people = people;
    }

    @Transactional(readOnly = true)
    public List<Person> list(Long userId, boolean activeOnly) {
        return activeOnly
                ? people.findByUserIdAndActiveOrderByIdAsc(userId, true)
                : people.findByUserIdOrderByIdAsc(userId);
    }

    @Transactional
    public Person create(Long userId, String name, String color) {
        if (people.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new PersonNameTakenException();
        }
        Person person = new Person(userId, name);
        person.setColor(color);
        return people.save(person);
    }

    @Transactional
    public Person update(Long userId, Long id, String name, boolean active, String color) {
        Person person = people.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (people.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, person.getId())) {
            throw new PersonNameTakenException();
        }
        person.update(name, active);
        person.setColor(color);
        return person;
    }
}
