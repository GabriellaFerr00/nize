package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.domain.Category;
import br.com.nize.domain.Expense;
import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.Income;
import br.com.nize.domain.LoadedMonth;
import br.com.nize.domain.LoadedMonthId;
import br.com.nize.domain.Person;
import br.com.nize.domain.UserYearId;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.CategoryRepository;
import br.com.nize.repository.ExpenseRepository;
import br.com.nize.repository.IncomeRepository;
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
 * Opens a month for the first time: carries over what {@link MonthLoad} decides, for expenses and for incomes in
 * installments (REQ-022, REQ-025). Runs once per user and
 * month (D-7); opening again does nothing, even if the user deleted what was carried over.
 */
@Service
public class MonthLoadService {

    private final LoadedMonthRepository loaded;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final CategoryRepository categories;
    private final CardRepository cards;
    private final PersonRepository people;
    private final SettingsRepository settings;
    private final UserYearRepository years;
    private final YearService yearService;

    public MonthLoadService(LoadedMonthRepository loaded, ExpenseRepository expenses, IncomeRepository incomes,
                            CategoryRepository categories,
                            CardRepository cards, PersonRepository people, SettingsRepository settings,
                            UserYearRepository years, YearService yearService) {
        this.loaded = loaded;
        this.expenses = expenses;
        this.incomes = incomes;
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

        String previousMonth = month.minusMonths(1).toString();
        boolean automaticInstallments = settings.findById(userId).orElseThrow().isAutoInstallments();
        loadExpenses(userId, month, expenses.findByUserIdAndMonthOrderByIdAsc(userId, previousMonth), automaticInstallments);
        loadIncomes(userId, month, incomes.findByUserIdAndMonthOrderByIdAsc(userId, previousMonth), automaticInstallments);
    }

    /**
     * After deleting only installment N of M (REQ-050): the load continues installments from the previous month, so
     * if N+1 does not exist yet and the next month was not opened, N+1 is created there now; otherwise the next
     * installments would never come. Nothing happens with automatic installments off (as in the load).
     */
    @Transactional
    public void keepInstallmentsComing(Long userId, Expense deleted) {
        YearMonth next = nextMonthToFill(userId, deleted.getMonth(), deleted.getInstallmentGroupId(),
                deleted.getInstallmentNumber(), deleted.getInstallmentCount());
        if (next == null) {
            return;
        }
        int nextNumber = deleted.getInstallmentNumber() + 1;
        boolean exists = expenses.findByUserIdAndInstallmentGroupIdIn(userId, Set.of(deleted.getInstallmentGroupId()))
                .stream().anyMatch(e -> Integer.valueOf(nextNumber).equals(e.getInstallmentNumber()));
        Expense created = exists ? null : copy(userId, next, deleted, nextNumber);
        if (created != null) {
            expenses.save(created);
        }
    }

    /** The same as {@link #keepInstallmentsComing(Long, Expense)}, for an income in installments. */
    @Transactional
    public void keepInstallmentsComing(Long userId, Income deleted) {
        YearMonth next = nextMonthToFill(userId, deleted.getMonth(), deleted.getInstallmentGroupId(),
                deleted.getInstallmentNumber(), deleted.getInstallmentCount());
        if (next == null) {
            return;
        }
        int nextNumber = deleted.getInstallmentNumber() + 1;
        boolean exists = incomes.findByUserIdAndInstallmentGroupIdIn(userId, Set.of(deleted.getInstallmentGroupId()))
                .stream().anyMatch(i -> Integer.valueOf(nextNumber).equals(i.getInstallmentNumber()));
        Income created = exists ? null : copy(userId, next, deleted, nextNumber);
        if (created != null) {
            incomes.save(created);
        }
    }

    /** The next month, when an installment that is not the last was deleted and that month was not opened yet. */
    private YearMonth nextMonthToFill(Long userId, String monthRef, String group, Integer number, Integer count) {
        if (group == null || number == null || count == null || number >= count) {
            return null;
        }
        if (!settings.findById(userId).orElseThrow().isAutoInstallments()) {
            return null;
        }
        YearMonth next = ExpenseService.parseMonth(monthRef).plusMonths(1);
        // a year the user lacks is not created here: the references would not exist yet
        if (loaded.existsById(new LoadedMonthId(userId, next.toString()))
                || !years.existsById(new UserYearId(userId, next.getYear()))) {
            return null;
        }
        return next;
    }

    private void loadExpenses(Long userId, YearMonth month, List<Expense> previous, boolean automaticInstallments) {
        if (previous.isEmpty()) {
            return;
        }
        Map<String, Expense> byKey = new HashMap<>();
        previous.forEach(e -> byKey.put(String.valueOf(e.getId()), e));

        List<Decision> decisions = MonthLoad.decide(
                previous.stream().map(MonthLoadService::toRuleInput).toList(),
                existingInstallments(userId, previous),
                automaticInstallments);

        List<Expense> created = decisions.stream()
                .map(d -> copy(userId, month, byKey.get(d.sourceKey()), d.nextNumber()))
                .filter(java.util.Objects::nonNull)
                .toList();
        expenses.saveAll(created);
    }

    /** Incomes are never copied as such; only the next installment of an income in installments is created. */
    private void loadIncomes(Long userId, YearMonth month, List<Income> previous, boolean automaticInstallments) {
        List<Income> inInstallments = previous.stream().filter(i -> i.getInstallmentGroupId() != null).toList();
        if (inInstallments.isEmpty()) {
            return;
        }
        Map<String, Income> byKey = new HashMap<>();
        inInstallments.forEach(i -> byKey.put(String.valueOf(i.getId()), i));
        Set<InstallmentKey> existing = new HashSet<>();
        incomes.findByUserIdAndInstallmentGroupIdIn(userId,
                        inInstallments.stream().map(Income::getInstallmentGroupId).collect(Collectors.toSet()))
                .forEach(i -> existing.add(new InstallmentKey(i.getInstallmentGroupId(), i.getInstallmentNumber())));

        List<Decision> decisions = MonthLoad.decide(
                inInstallments.stream().map(i -> new PreviousExpense(String.valueOf(i.getId()), false,
                        i.getInstallmentGroupId(), i.getInstallmentNumber(), i.getInstallmentCount())).toList(),
                existing, automaticInstallments);

        List<Income> created = decisions.stream()
                .map(d -> copy(userId, month, byKey.get(d.sourceKey()), d.nextNumber()))
                .filter(java.util.Objects::nonNull)
                .toList();
        incomes.saveAll(created);
    }

    /** The next installment of an income; null when its person no longer exists in the target year. */
    private Income copy(Long userId, YearMonth month, Income source, int nextNumber) {
        Long personId = source.getPersonId() == null ? null : mapPerson(userId, month.getYear(), source.getPersonId());
        if (source.getPersonId() != null && personId == null) {
            return null;
        }
        LocalDate date = month.atDay(Math.min(source.getDate().getDayOfMonth(), month.lengthOfMonth()));
        return new Income(userId, month.toString(), source.getName(), date, source.getAmountCents(), personId,
                nextNumber, source.getInstallmentCount(), source.getInstallmentGroupId());
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
