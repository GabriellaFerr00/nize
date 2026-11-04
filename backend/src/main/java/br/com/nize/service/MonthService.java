package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.domain.Expense;
import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.Income;
import br.com.nize.domain.PaymentMethod;
import br.com.nize.domain.Settings;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.ExpenseRepository;
import br.com.nize.repository.IncomeRepository;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.rules.ClosingPositions;
import br.com.nize.rules.ClosingPositions.Position;
import br.com.nize.rules.MonthTotals;
import br.com.nize.rules.OwnBalance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The month screen (REQ-041): opens the month (load on first open, REQ-022) and computes totals, positions and own
 * balance from the rules on every read. Nothing derived is stored (NFR-002).
 */
@Service
public class MonthService {

    /** Totals of the month; {@code balanceCents} and {@code ownBalanceCents} may be negative. */
    public record Totals(long totalExpensesCents, long totalFixedCents, long totalIncomeCents, long balanceCents,
                         long thirdPartyExpensesCents, long reimbursementsCents, long ownBalanceCents) {
    }

    public record MonthView(String month, List<Expense> expenses, List<Income> incomes, Totals totals,
                            boolean closingsEnabled, List<Position> positions) {
    }

    private final MonthLoadService loader;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final CardRepository cards;
    private final SettingsRepository settings;

    public MonthService(MonthLoadService loader, ExpenseRepository expenses, IncomeRepository incomes,
                        CardRepository cards, SettingsRepository settings) {
        this.loader = loader;
        this.expenses = expenses;
        this.incomes = incomes;
        this.cards = cards;
        this.settings = settings;
    }

    @Transactional
    public MonthView open(Long userId, String monthRef) {
        YearMonth month = ExpenseService.parseMonth(monthRef);
        loader.open(userId, month.toString());

        List<Expense> monthExpenses = expenses.findByUserIdAndMonthOrderByIdAsc(userId, month.toString());
        List<Income> monthIncomes = incomes.findByUserIdAndMonthOrderByIdAsc(userId, month.toString());

        MonthTotals.Totals base = MonthTotals.of(
                monthExpenses.stream().map(e -> new MonthTotals.Expense(
                        e.getType() == ExpenseType.FIXED ? MonthTotals.Kind.FIXED : MonthTotals.Kind.CARD,
                        e.getAmountCents())).toList(),
                monthIncomes.stream().map(Income::getAmountCents).toList());
        List<Long> thirdParty = monthExpenses.stream().filter(e -> e.getPersonId() != null).map(Expense::getAmountCents).toList();
        List<Long> reimbursements = monthIncomes.stream().filter(i -> i.getPersonId() != null).map(Income::getAmountCents).toList();
        Totals totals = new Totals(base.totalExpensesCents(), base.totalFixedCents(), base.totalIncomeCents(),
                base.balanceCents(), sum(thirdParty), sum(reimbursements),
                OwnBalance.of(base.balanceCents(), thirdParty, reimbursements));

        Settings userSettings = settings.findById(userId).orElseThrow();
        List<Position> positions = userSettings.isClosingsEnabled()
                ? positions(userId, month, monthExpenses, monthIncomes, userSettings.getClosingDays())
                : List.of();

        return new MonthView(month.toString(), monthExpenses, monthIncomes, totals, userSettings.isClosingsEnabled(), positions);
    }

    private List<Position> positions(Long userId, YearMonth month, List<Expense> monthExpenses, List<Income> monthIncomes,
                                     List<Integer> closingDays) {
        Map<Long, Integer> dueDayByCard = cards.findByUserIdAndYearNumberOrderByIdAsc(userId, month.getYear()).stream()
                .collect(Collectors.toMap(Card::getId, Card::getDueDay));
        return ClosingPositions.of(month,
                monthExpenses.stream().map(e -> new ClosingPositions.Expense(e.getAmountCents(),
                        e.getPaymentMethod() == PaymentMethod.CREDIT ? ClosingPositions.Payment.CREDIT : ClosingPositions.Payment.PIX,
                        e.getDate(), e.getCardId() == null ? null : dueDayByCard.get(e.getCardId()))).toList(),
                monthIncomes.stream().map(i -> new ClosingPositions.Income(i.getAmountCents(), i.getDate())).toList(),
                closingDays);
    }

    private static long sum(List<Long> cents) {
        long total = 0;
        for (long value : cents) {
            total = Math.addExact(total, value);
        }
        return total;
    }
}
