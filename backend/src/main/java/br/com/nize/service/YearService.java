package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.domain.CardLogo;
import br.com.nize.domain.Category;
import br.com.nize.domain.Person;
import br.com.nize.domain.UserYear;
import br.com.nize.domain.UserYearId;
import br.com.nize.repository.CardLogoRepository;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.CategoryRepository;
import br.com.nize.repository.PersonRepository;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.repository.UserYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Years of a user and the creation of a new year from the previous one (REQ-007, REQ-008). */
@Service
public class YearService {

    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    public record YearInfo(int year, boolean current) {
    }

    private final UserYearRepository years;
    private final SettingsRepository settings;
    private final CategoryRepository categories;
    private final CardRepository cards;
    private final PersonRepository people;
    private final CategorySeeder categorySeeder;
    private final CardLogoRepository logos;

    public YearService(UserYearRepository years, SettingsRepository settings, CategoryRepository categories,
                       CardRepository cards, PersonRepository people, CategorySeeder categorySeeder, CardLogoRepository logos) {
        this.years = years;
        this.settings = settings;
        this.categories = categories;
        this.cards = cards;
        this.people = people;
        this.categorySeeder = categorySeeder;
        this.logos = logos;
    }

    @Transactional(readOnly = true)
    public List<YearInfo> list(Long userId) {
        int current = settings.findById(userId).orElseThrow().getCurrentYear();
        return years.findYearNumbers(userId).stream().map(y -> new YearInfo(y, y == current)).toList();
    }

    /**
     * Creates the year and copies categories, cards and people (with their active flag) from the closest
     * earlier year. With no earlier year it starts from the factory categories. Copies are independent rows.
     */
    @Transactional
    public int create(Long userId, int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new InvalidYearException(year);
        }
        if (years.existsById(new UserYearId(userId, year))) {
            throw new YearAlreadyExistsException(year);
        }
        years.saveAndFlush(new UserYear(userId, year));

        Integer source = years.findClosestEarlierYear(userId, year).orElse(null);
        if (source == null) {
            categorySeeder.seedFactoryCategories(userId, year);
            return year;
        }
        categories.saveAll(categories.findByUserIdAndYearNumberOrderByIdAsc(userId, source).stream().map(c -> {
            Category copy = new Category(userId, year, c.getName(), c.getDescription());
            copy.update(c.getName(), c.getDescription(), c.isActive());
            copy.setMonthlyLimitCents(c.getMonthlyLimitCents());
            copy.setColor(c.getColor());
            return copy;
        }).toList());
        List<Card> sourceCards = cards.findByUserIdAndYearNumberOrderByIdAsc(userId, source);
        List<Card> copiedCards = cards.saveAll(sourceCards.stream().map(c -> {
            Card copy = new Card(userId, year, c.getName(), c.getDueDay());
            copy.update(c.getName(), c.getDueDay(), c.isActive());
            copy.setBrand(c.getBrand());
            return copy;
        }).toList());
        // the card's own image goes with it (REQ-052)
        for (int i = 0; i < sourceCards.size(); i++) {
            Long newId = copiedCards.get(i).getId();
            logos.findByCardIdAndUserId(sourceCards.get(i).getId(), userId)
                    .ifPresent(logo -> logos.save(new CardLogo(newId, userId, logo.getContentType(), logo.getData())));
        }
        people.saveAll(people.findByUserIdAndYearNumberOrderByIdAsc(userId, source).stream().map(p -> {
            Person copy = new Person(userId, year, p.getName());
            copy.update(p.getName(), p.isActive());
            copy.setColor(p.getColor());
            return copy;
        }).toList());
        return year;
    }
}
