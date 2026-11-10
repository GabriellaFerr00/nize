package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.domain.Category;
import br.com.nize.domain.Expense;
import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.PaymentMethod;
import br.com.nize.domain.Person;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.CategoryRepository;
import br.com.nize.repository.ExpenseRepository;
import br.com.nize.repository.PersonRepository;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.repository.UserYearRepository;
import br.com.nize.domain.UserYearId;
import br.com.nize.rules.Installments;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * Expense rules. The user id comes from the token (D-6); categories, cards and people of other users or of
 * another user are "not found". Consistency rules: see "Regras de consistência do lançamento" in design.md.
 */
@Service
public class ExpenseService {

    static final int MAX_INSTALLMENTS = 120;

    /**
     * Everything the caller may set. {@code generateInstallments}: the purchase is entered once and the system
     * creates one installment per month ({@code amountIsTotal} says whether {@code amountCents} is the total or
     * the value of each installment). A manual installment uses installmentNumber/installmentCount instead.
     */
    public record NewExpense(String name, Long categoryId, long amountCents, ExpenseType type,
                             PaymentMethod paymentMethod, Long cardId, Long personId, Boolean paid,
                             LocalDate date, Integer installmentNumber, Integer installmentCount,
                             boolean generateInstallments, boolean amountIsTotal) {
    }

    private final ExpenseRepository expenses;
    private final CategoryRepository categories;
    private final CardRepository cards;
    private final PersonRepository people;
    private final SettingsRepository settings;
    private final UserYearRepository years;
    private final YearService yearService;
    private final YearGuard yearGuard;
    private final MonthLoadService monthLoad;

    public ExpenseService(ExpenseRepository expenses, CategoryRepository categories, CardRepository cards,
                          PersonRepository people, SettingsRepository settings, UserYearRepository years,
                          YearService yearService, YearGuard yearGuard, MonthLoadService monthLoad) {
        this.expenses = expenses;
        this.categories = categories;
        this.cards = cards;
        this.people = people;
        this.settings = settings;
        this.years = years;
        this.yearService = yearService;
        this.yearGuard = yearGuard;
        this.monthLoad = monthLoad;
    }

    /**
     * Creates the expense of the requested month. A manual installment creates only that installment (REQ-019).
     * With generation on and the permission on, creates one installment per month, the first one in the
     * requested month (REQ-018); otherwise a single expense (REQ-009). Returns the created expenses in month order.
     */
    @Transactional
    public List<Expense> create(Long userId, String monthRef, NewExpense in) {
        YearMonth month = parseMonth(monthRef);
        yearGuard.requireYear(userId, month.getYear());
        if (!month.equals(YearMonth.from(in.date()))) {
            throw new InvalidExpenseException("DATE_OUT_OF_MONTH");
        }

        boolean wantsGeneration = in.generateInstallments() && in.installmentCount() != null && in.installmentCount() > 1;
        if (in.generateInstallments() && in.installmentNumber() != null) {
            throw new InvalidExpenseException("INVALID_EXPENSE"); // "generate" and "this is installment N" exclude each other
        }
        boolean generate = wantsGeneration && settings.findById(userId).orElseThrow().isAutoInstallments();
        if (in.generateInstallments() && in.installmentCount() != null && in.installmentCount() < 1) {
            throw new InvalidExpenseException("INVALID_EXPENSE");
        }
        // generation off (no count, 1 installment or permission off): a plain single expense
        NewExpense single = in.generateInstallments() ? withoutInstallments(in) : in;

        PaymentMethod payment = in.paymentMethod() != null
                ? in.paymentMethod()
                : (in.type() == ExpenseType.CARD ? PaymentMethod.CREDIT : PaymentMethod.PIX);
        validateCombination(generate ? in : single, payment, generate);

        References refs = resolve(userId, in.categoryId(), in.cardId(), in.personId(), null);
        if (!generate) {
            // a manual installment gets its own group, so the month load can continue it without duplicating (D-8)
            String group = single.installmentNumber() != null ? UUID.randomUUID().toString() : null;
            return List.of(expenses.save(build(userId, month, single, payment, in.amountCents(), refs, null, null, group, in.date())));
        }
        return generateInstallments(userId, month, in, payment, refs);
    }

    private List<Expense> generateInstallments(Long userId, YearMonth first, NewExpense in, PaymentMethod payment,
                                               References firstRefs) {
        int count = in.installmentCount();
        List<Long> amounts;
        try {
            amounts = in.amountIsTotal()
                    ? Installments.splitTotal(in.amountCents(), count)
                    : Installments.repeatValue(in.amountCents(), count);
        } catch (IllegalArgumentException e) {
            throw new InvalidExpenseException("INVALID_EXPENSE");
        }
        String group = UUID.randomUUID().toString();
        List<Expense> created = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            YearMonth month = first.plusMonths(i);
            // catalogs have no year (D-22): the same category, card and person; a later year is only opened
            if (month.getYear() != first.getYear() && !years.existsById(new UserYearId(userId, month.getYear()))) {
                yearService.create(userId, month.getYear());
            }
            References refs = firstRefs;
            LocalDate date = month.atDay(Math.min(in.date().getDayOfMonth(), month.lengthOfMonth()));
            created.add(build(userId, month, in, payment, amounts.get(i), refs, i + 1, count, group, date,
                    i == 0 && Boolean.TRUE.equals(in.paid())));
        }
        return expenses.saveAll(created);
    }

    private record References(Category category, Card card, Person person) {
    }

    /**
     * Finds category, card and person of the user (no year, D-22). Inactive ones are refused, except those the
     * expense already used ({@code current}): an old expense stays editable after its category is deactivated.
     */
    private References resolve(Long userId, Long categoryId, Long cardId, Long personId, Expense current) {
        Category category = categories.findByIdAndUserId(categoryId, userId).orElseThrow(ResourceNotFoundException::new);
        Card card = cardId == null ? null
                : cards.findByIdAndUserId(cardId, userId).orElseThrow(ResourceNotFoundException::new);
        Person person = personId == null ? null
                : people.findByIdAndUserId(personId, userId).orElseThrow(ResourceNotFoundException::new);
        boolean inactive =
                (!category.isActive() && (current == null || !Objects.equals(categoryId, current.getCategoryId())))
                || (card != null && !card.isActive() && (current == null || !Objects.equals(cardId, current.getCardId())))
                || (person != null && !person.isActive() && (current == null || !Objects.equals(personId, current.getPersonId())));
        if (inactive) {
            throw new InvalidExpenseException("INACTIVE_REFERENCE");
        }
        return new References(category, card, person);
    }

    private static Expense build(Long userId, YearMonth month, NewExpense in, PaymentMethod payment, long amount,
                                 References refs, Integer number, Integer count, String group, LocalDate date) {
        return build(userId, month, in, payment, amount, refs, number, count, group, date, Boolean.TRUE.equals(in.paid()));
    }

    private static Expense build(Long userId, YearMonth month, NewExpense in, PaymentMethod payment, long amount,
                                 References refs, Integer number, Integer count, String group, LocalDate date, boolean paid) {
        Integer n = number != null ? number : in.installmentNumber();
        Integer c = count != null ? count : in.installmentCount();
        return new Expense(userId, month.toString(), in.name(), refs.category().getId(), amount, in.type(), payment,
                refs.card() == null ? null : refs.card().getId(), refs.person() == null ? null : refs.person().getId(),
                paid, date, n, c, group);
    }

    private static NewExpense withoutInstallments(NewExpense in) {
        return new NewExpense(in.name(), in.categoryId(), in.amountCents(), in.type(), in.paymentMethod(),
                in.cardId(), in.personId(), in.paid(), in.date(), null, null, false, true);
    }

    private static void validateCombination(NewExpense in, PaymentMethod payment, boolean generating) {
        if (in.type() == ExpenseType.CARD && payment != PaymentMethod.CREDIT) {
            throw new InvalidExpenseException("INVALID_EXPENSE");
        }
        if (in.cardId() != null && payment != PaymentMethod.CREDIT) {
            throw new InvalidExpenseException("INVALID_EXPENSE");
        }
        // REQ-015/016 (2026-11-09): credit always needs a card, on create and on edit; old rows without one stay valid
        if (payment == PaymentMethod.CREDIT && in.cardId() == null) {
            throw new InvalidExpenseException("CARD_REQUIRED");
        }
        if (in.personId() != null && in.type() != ExpenseType.CARD) {
            throw new InvalidExpenseException("INVALID_EXPENSE");
        }
        if (generating) {
            boolean ok = in.type() == ExpenseType.CARD && in.installmentCount() <= MAX_INSTALLMENTS;
            if (!ok) {
                throw new InvalidExpenseException("INVALID_EXPENSE");
            }
            return;
        }
        boolean hasNumber = in.installmentNumber() != null;
        boolean hasCount = in.installmentCount() != null;
        if (hasNumber != hasCount) {
            throw new InvalidExpenseException("INVALID_EXPENSE");
        }
        if (hasNumber) {
            boolean ok = in.type() == ExpenseType.CARD
                    && in.installmentNumber() >= 1
                    && in.installmentNumber() <= in.installmentCount()
                    && in.installmentCount() <= MAX_INSTALLMENTS;
            if (!ok) {
                throw new InvalidExpenseException("INVALID_EXPENSE");
            }
        }
    }

    static YearMonth parseMonth(String monthRef) {
        try {
            return YearMonth.parse(monthRef);
        } catch (DateTimeParseException e) {
            throw new InvalidExpenseException("INVALID_MONTH");
        }
    }

    /** Everything the caller may change on an existing expense (the month cannot change). */
    public record ExpenseEdit(String name, Long categoryId, long amountCents, ExpenseType type,
                              PaymentMethod paymentMethod, Long cardId, Long personId, boolean paid, LocalDate date) {
    }

    @Transactional
    public Expense update(Long userId, Long id, ExpenseEdit in) {
        Expense expense = expenses.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        YearMonth month = YearMonth.parse(expense.getMonth());
        if (!month.equals(YearMonth.from(in.date()))) {
            throw new InvalidExpenseException("DATE_OUT_OF_MONTH");
        }
        PaymentMethod payment = in.paymentMethod() != null
                ? in.paymentMethod()
                : (in.type() == ExpenseType.CARD ? PaymentMethod.CREDIT : PaymentMethod.PIX);
        // the installment data is kept, so the same consistency rules decide if the new type still fits
        validateCombination(new NewExpense(in.name(), in.categoryId(), in.amountCents(), in.type(), payment,
                in.cardId(), in.personId(), in.paid(), in.date(), expense.getInstallmentNumber(),
                expense.getInstallmentCount(), false, true), payment, false);
        References refs = resolve(userId, in.categoryId(), in.cardId(), in.personId(), expense);

        expense.update(in.name(), refs.category().getId(), in.amountCents(), in.type(), payment,
                refs.card() == null ? null : refs.card().getId(),
                refs.person() == null ? null : refs.person().getId(), in.paid(), in.date());
        return expense;
    }

    @Transactional
    public Expense setPaid(Long userId, Long id, boolean paid) {
        Expense expense = expenses.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        expense.setPaid(paid);
        return expense;
    }

    @Transactional
    public void delete(Long userId, Long id, DeleteScope scope) {
        Expense expense = expenses.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        String group = expense.getInstallmentGroupId();
        Integer number = expense.getInstallmentNumber();
        if (scope == DeleteScope.FOLLOWING && group != null && number != null) {
            // this installment and the ones after it (later months) of the same purchase (REQ-050)
            expenses.deleteAll(expenses.findByUserIdAndInstallmentGroupIdIn(userId, List.of(group)).stream()
                    .filter(e -> e.getInstallmentNumber() != null && e.getInstallmentNumber() >= number)
                    .toList());
            return;
        }
        expenses.delete(expense);
        monthLoad.keepInstallmentsComing(userId, expense);
    }
}
