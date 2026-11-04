package br.com.nize.service;

import br.com.nize.domain.Expense;
import br.com.nize.domain.Income;
import br.com.nize.repository.ExpenseRepository;
import br.com.nize.repository.IncomeRepository;
import br.com.nize.rules.MonthTotals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The annual overview (REQ-039): expenses, incomes and balance of each of the 12 months, computed on every read
 * with the same rule as the month screen (NFR-002). Before reading, it opens every month of the year up to the
 * current one, January first, so each month is what {@code GET /api/months/{m}} would show (D-19).
 */
@Service
public class OverviewService {

    /** One month of the overview; {@code balanceCents} may be negative. */
    public record MonthEntry(String month, long totalExpensesCents, long totalIncomeCents, long balanceCents) {
    }

    private final YearGuard yearGuard;
    private final MonthLoadService loader;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;

    public OverviewService(YearGuard yearGuard, MonthLoadService loader, ExpenseRepository expenses,
                           IncomeRepository incomes) {
        this.yearGuard = yearGuard;
        this.loader = loader;
        this.expenses = expenses;
        this.incomes = incomes;
    }

    @Transactional
    public List<MonthEntry> of(Long userId, int year) {
        yearGuard.requireYear(userId, year);
        List<String> months = IntStream.rangeClosed(1, 12).mapToObj(m -> YearMonth.of(year, m).toString()).toList();

        YearMonth current = YearMonth.now();
        months.stream().filter(m -> !YearMonth.parse(m).isAfter(current)).forEach(m -> loader.open(userId, m));

        Map<String, List<Expense>> expensesByMonth = expenses.findByUserIdAndMonthIn(userId, months).stream()
                .collect(Collectors.groupingBy(Expense::getMonth));
        Map<String, List<Income>> incomesByMonth = incomes.findByUserIdAndMonthIn(userId, months).stream()
                .collect(Collectors.groupingBy(Income::getMonth));

        return months.stream().map(m -> {
            MonthTotals.Totals totals = MonthService.totalsOf(expensesByMonth.getOrDefault(m, List.of()),
                    incomesByMonth.getOrDefault(m, List.of()));
            return new MonthEntry(m, totals.totalExpensesCents(), totals.totalIncomeCents(), totals.balanceCents());
        }).toList();
    }
}
