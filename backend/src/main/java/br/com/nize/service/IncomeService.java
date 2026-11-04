package br.com.nize.service;

import br.com.nize.domain.Income;
import br.com.nize.domain.Person;
import br.com.nize.repository.IncomeRepository;
import br.com.nize.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Income rules (REQ-024..026). The user id comes from the token (D-6); a person of another user or of another year is
 * "not found". An installment income (N of M) gets its own group so the month load continues it (REQ-025, D-8).
 */
@Service
public class IncomeService {

    public record NewIncome(String name, long amountCents, LocalDate date, Long personId,
                            Integer installmentNumber, Integer installmentCount) {
    }

    public record IncomeEdit(String name, long amountCents, LocalDate date, Long personId) {
    }

    private final IncomeRepository incomes;
    private final PersonRepository people;
    private final YearGuard yearGuard;

    public IncomeService(IncomeRepository incomes, PersonRepository people, YearGuard yearGuard) {
        this.incomes = incomes;
        this.people = people;
        this.yearGuard = yearGuard;
    }

    @Transactional
    public Income create(Long userId, String monthRef, NewIncome in) {
        YearMonth month = ExpenseService.parseMonth(monthRef);
        yearGuard.requireYear(userId, month.getYear());
        requireDateInMonth(in.date(), month);
        validateInstallments(in.installmentNumber(), in.installmentCount());
        Long personId = resolvePerson(userId, month.getYear(), in.personId(), null);
        String group = in.installmentNumber() != null ? UUID.randomUUID().toString() : null;
        return incomes.save(new Income(userId, month.toString(), in.name(), in.date(), in.amountCents(), personId,
                in.installmentNumber(), in.installmentCount(), group));
    }

    @Transactional
    public Income update(Long userId, Long id, IncomeEdit in) {
        Income income = incomes.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        YearMonth month = YearMonth.parse(income.getMonth());
        requireDateInMonth(in.date(), month);
        Long personId = resolvePerson(userId, month.getYear(), in.personId(), income.getPersonId());
        income.update(in.name(), in.date(), in.amountCents(), personId);
        return income;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        incomes.delete(incomes.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new));
    }

    private static void requireDateInMonth(LocalDate date, YearMonth month) {
        if (!month.equals(YearMonth.from(date))) {
            throw new InvalidExpenseException("DATE_OUT_OF_MONTH");
        }
    }

    private static void validateInstallments(Integer number, Integer count) {
        if (number == null && count == null) {
            return;
        }
        boolean ok = number != null && count != null
                && number >= 1 && number <= count && count <= ExpenseService.MAX_INSTALLMENTS;
        if (!ok) {
            throw new InvalidExpenseException("INVALID_INCOME");
        }
    }

    /** An inactive person is refused, except the one the income already had (it stays editable). */
    private Long resolvePerson(Long userId, int year, Long personId, Long currentPersonId) {
        if (personId == null) {
            return null;
        }
        Person person = people.findByIdAndUserId(personId, userId)
                .filter(p -> p.getYearNumber() == year).orElseThrow(ResourceNotFoundException::new);
        if (!person.isActive() && !Objects.equals(personId, currentPersonId)) {
            throw new InvalidExpenseException("INACTIVE_REFERENCE");
        }
        return person.getId();
    }
}
