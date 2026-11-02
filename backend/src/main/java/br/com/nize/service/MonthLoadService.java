package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.domain.Category;
import br.com.nize.domain.Expense;
import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.LoadedMonth;
import br.com.nize.domain.LoadedMonthId;
import br.com.nize.domain.Person;
import br.com.nize.domain.UserYearId;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.CategoryRepository;
import br.com.nize.repository.ExpenseRepository;
import br.com.nize.repository.LoadedMonthRepository;
import br.com.nize.repository.PersonRepository;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.repository.UserYearRepository;
import br.com.nize.rules.MonthLoad;
import br.com.nize.rules.MonthLoad.Decision;
import br.com.nize.rules.MonthLoad.InstallmentKey;
import br.com.nize.rules.MonthLoad.PreviousExpense;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Opens a month for the first time: carries over what {@link MonthLoad} decides (REQ-022). Runs once per user and
 * month (D-7); opening again does nothing, even if the user deleted what was carried over.
 */
@Service
public class MonthLoadService {

    private final LoadedMonthRepository loaded;
    private final ExpenseRepository expenses;
    private final CategoryRepository categories;
    private final CardRepository cards;
    private final PersonRepository people;
    private final SettingsRepository settings;
    private final UserYearRepository years;
    private final YearService yearService;

    public MonthLoadService(LoadedMonthRepository loaded, ExpenseRepository expenses, CategoryRepository categories,
                            CardRepository cards, PersonRepository people, SettingsRepository settings,
                            UserYearRepository years, YearService yearService) {
        this.loaded = loaded;
        this.expenses = expenses;
        this.categories = categories;
        this.cards = cards;
        this.people = people;
        this.settings = settings;
        this.years = years;
        this.yearService = yearService;
    }

    @Transactional
    public void open(Long userId, String monthRef) {
        YearMonth month = ExpenseService.parseMonth(monthRef);
        ensureYear(userId, month.getYear());
        LoadedMonthId id = new LoadedMonthId(userId, month.toString());
        if (loaded.existsById(id)) {
            return;
        }
        loaded.saveAndFlush(new LoadedMonth(userId, month.toString(), Instant.now()));

        YearMonth previousMonth = month.minusMonths(1);
        List<Expense> previous = expenses.findByUserIdAndMonthOrderByIdAsc(userId, previousMonth.toString());
        if (previous.isEmpty()) {
            return;
        }
        Map<String, Expense> byKey = new HashMap<>();
        previous.forEach(e -> byKey.put(String.valueOf(e.getId()), e));

        List<Decision> decisions = MonthLoad.decide(
                previous.stream().map(MonthLoadService::toRuleInput).toList(),
                existingInstallments(userId, previous),
                settings.findById(userId).orElseThrow().isAutoInstallments());

        List<Expense> created = decisions.stream()
                .map(d -> copy(userId, month, byKey.get(d.sourceKey()), d.nextNumber()))
                .filter(java.util.Objects::nonNull)
                .toList();
        expenses.saveAll(created);
    }

    private static PreviousExpense toRuleInput(Expense e) {
        return new PreviousExpense(String.valueOf(e.getId()), e.getType() == ExpenseType.FIXED,
                e.getInstallmentGroupId(), e.getInstallmentNumber(), e.getInstallmentCount());
    }

    private Set<InstallmentKey> existingInstallments(Long userId, List<Expense> previous) {
        Set<String> groups = previous.stream().map(Expense::getInstallmentGroupId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (groups.isEmpty()) {
            return Set.of();
        }
        Set<InstallmentKey> keys = new HashSet<>();
        expenses.findByUserIdAndInstallmentGroupIdIn(userId, groups)
                .forEach(e -> keys.add(new InstallmentKey(e.getInstallmentGroupId(), e.getInstallmentNumber())));
        return keys;
    }

    /** A month of a year the user lacks is only reachable as the very next year (Dec to Jan opens it). */
    private void ensureYear(Long userId, int year) {
        if (years.existsById(new UserYearId(userId, year))) {
            return;
        }
        int latest = years.findYearNumbers(userId).stream().mapToInt(Integer::intValue).max().orElse(Integer.MIN_VALUE);
        if (year != latest + 1) {
            throw new ResourceNotFoundException();
        }
        yearService.create(userId, year);
    }

    /** Copies one expense into the target month; null when its category (or card, person) no longer exists there. */
    private Expense copy(Long userId, YearMonth month, Expense source, Integer nextNumber) {
        int year = month.getYear();
        Long categoryId = mapCategory(userId, year, source.getCategoryId());
        Long cardId = source.getCardId() == null ? null : mapCard(userId, year, source.getCardId());
        Long personId = source.getPersonId() == null ? null : mapPerson(userId, year, source.getPersonId());
        boolean missing = categoryId == null
                || (source.getCardId() != null && cardId == null)
                || (source.getPersonId() != null && personId == null);
        if (missing) {
            return null;
        }
        LocalDate date = month.atDay(Math.min(source.getDate().getDayOfMonth(), month.lengthOfMonth()));
        boolean installment = nextNumber != null;
        return new Expense(userId, month.toString(), source.getName(), categoryId, source.getAmountCents(),
                source.getType(), source.getPaymentMethod(), cardId, personId, false, date,
                installment ? nextNumber : null, installment ? source.getInstallmentCount() : null,
                installment ? source.getInstallmentGroupId() : null);
    }

    private Long mapCategory(Long userId, int year, Long sourceId) {
        Category source = categories.findByIdAndUserId(sourceId, userId).orElse(null);
        if (source == null) {
            return null;
        }
        if (source.getYearNumber() == year) {
            return source.getId();
        }
        return categories.findByUserIdAndYearNumberAndNameIgnoreCase(userId, year, source.getName()).map(Category::getId).orElse(null);
    }

    private Long mapCard(Long userId, int year, Long sourceId) {
        Card source = cards.findByIdAndUserId(sourceId, userId).orElse(null);
        if (source == null) {
            return null;
        }
        if (source.getYearNumber() == year) {
            return source.getId();
        }
        return cards.findByUserIdAndYearNumberAndNameIgnoreCase(userId, year, source.getName()).map(Card::getId).orElse(null);
    }

    private Long mapPerson(Long userId, int year, Long sourceId) {
        Person source = people.findByIdAndUserId(sourceId, userId).orElse(null);
        if (source == null) {
            return null;
        }
        if (source.getYearNumber() == year) {
            return source.getId();
        }
        return people.findByUserIdAndYearNumberAndNameIgnoreCase(userId, year, source.getName()).map(Person::getId).orElse(null);
    }
}
