package br.com.nize.service;

import br.com.nize.domain.Card;
import br.com.nize.domain.Category;
import br.com.nize.domain.Expense;
import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.Income;
import br.com.nize.domain.PaymentMethod;
import br.com.nize.domain.Person;
import br.com.nize.domain.Settings;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.CategoryRepository;
import br.com.nize.repository.ExpenseRepository;
import br.com.nize.repository.IncomeRepository;
import br.com.nize.repository.PersonRepository;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.rules.CategorySummary;
import br.com.nize.rules.ClosingPositions;
import br.com.nize.rules.ClosingPositions.Position;
import br.com.nize.rules.MonthTotals;
import br.com.nize.rules.OwnBalance;
import br.com.nize.rules.PaymentSummary;
import br.com.nize.rules.ThirdPartySummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The month screen (REQ-041): opens the month (load on first open, REQ-022) and computes totals, positions, own
 * balance and summaries from the rules on every read. Nothing derived is stored (NFR-002).
 * Names in the summaries are those of the month's year; a null card is "Sem cartão" (the front translates it).
 */
@Service
public class MonthService {

    /** Totals of the month; {@code balanceCents} and {@code ownBalanceCents} may be negative. */
    public record Totals(long totalExpensesCents, long totalFixedCents, long totalIncomeCents, long balanceCents,
                         long thirdPartyExpensesCents, long reimbursementsCents, long ownBalanceCents) {
    }

    public record CategoryEntry(Long categoryId, String categoryName, long amountCents, BigDecimal percent) {
    }

    public record CardEntry(Long cardId, String cardName, long amountCents) {
    }

    public record PaymentEntry(long pixCents, long creditCents) {
    }

    public record PersonEntry(Long personId, String personName, long amountCents, List<CardEntry> byCard) {
    }

    /** Σ byCategory = total expenses; pix + credit = total expenses; Σ byCard = credit; Σ byPerson = third party. */
    public record Summaries(List<CategoryEntry> byCategory, PaymentEntry byPayment, List<CardEntry> byCard,
                            List<PersonEntry> byPerson) {
    }

    public record MonthView(String month, List<Expense> expenses, List<Income> incomes, Totals totals,
                            boolean closingsEnabled, List<Position> positions, Summaries summaries) {
    }

    private final MonthLoadService loader;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final CardRepository cards;
    private final CategoryRepository categories;
    private final PersonRepository people;
    private final SettingsRepository settings;

    public MonthService(MonthLoadService loader, ExpenseRepository expenses, IncomeRepository incomes,
                        CardRepository cards, CategoryRepository categories, PersonRepository people,
                        SettingsRepository settings) {
        this.loader = loader;
        this.expenses = expenses;
        this.incomes = incomes;
        this.cards = cards;
        this.categories = categories;
        this.people = people;
        this.settings = settings;
    }

    @Transactional
    public MonthView open(Long userId, String monthRef) {
        YearMonth month = ExpenseService.parseMonth(monthRef);
        loader.open(userId, month.toString());

        List<Expense> monthExpenses = expenses.findByUserIdAndMonthOrderByIdAsc(userId, month.toString());
        List<Income> monthIncomes = incomes.findByUserIdAndMonthOrderByIdAsc(userId, month.toString());

        MonthTotals.Totals base = totalsOf(monthExpenses, monthIncomes);
        List<Long> thirdParty = monthExpenses.stream().filter(e -> e.getPersonId() != null).map(Expense::getAmountCents).toList();
        List<Long> reimbursements = monthIncomes.stream().filter(i -> i.getPersonId() != null).map(Income::getAmountCents).toList();
        Totals totals = new Totals(base.totalExpensesCents(), base.totalFixedCents(), base.totalIncomeCents(),
                base.balanceCents(), sum(thirdParty), sum(reimbursements),
                OwnBalance.of(base.balanceCents(), thirdParty, reimbursements));

        List<Card> yearCards = cards.findByUserIdAndYearNumberOrderByIdAsc(userId, month.getYear());
        Settings userSettings = settings.findById(userId).orElseThrow();
        List<Position> positions = userSettings.isClosingsEnabled()
                ? positions(month, monthExpenses, monthIncomes, userSettings.getClosingDays(), yearCards)
                : List.of();

        return new MonthView(month.toString(), monthExpenses, monthIncomes, totals, userSettings.isClosingsEnabled(),
                positions, summaries(userId, month.getYear(), monthExpenses, yearCards));
    }

    /** Totals and balance of one month from the rules; the overview uses it too, so both screens always agree. */
    static MonthTotals.Totals totalsOf(List<Expense> monthExpenses, List<Income> monthIncomes) {
        return MonthTotals.of(
                monthExpenses.stream().map(e -> new MonthTotals.Expense(
                        e.getType() == ExpenseType.FIXED ? MonthTotals.Kind.FIXED : MonthTotals.Kind.CARD,
                        e.getAmountCents())).toList(),
                monthIncomes.stream().map(Income::getAmountCents).toList());
    }

    private Summaries summaries(Long userId, int year, List<Expense> monthExpenses, List<Card> yearCards) {
        Map<Long, String> categoryNames = categories.findByUserIdAndYearNumberOrderByIdAsc(userId, year).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> cardNames = yearCards.stream().collect(Collectors.toMap(Card::getId, Card::getName));
        Map<Long, String> personNames = people.findByUserIdAndYearNumberOrderByIdAsc(userId, year).stream()
                .collect(Collectors.toMap(Person::getId, Person::getName));

        List<CategoryEntry> byCategory = CategorySummary.of(monthExpenses.stream()
                        .map(e -> new CategorySummary.Item(e.getCategoryId(), e.getAmountCents())).toList())
                .stream().map(c -> new CategoryEntry(c.categoryId(), categoryNames.get(c.categoryId()), c.amountCents(), c.percent()))
                .toList();
        PaymentSummary.Summary payments = PaymentSummary.of(monthExpenses.stream()
                .map(e -> new PaymentSummary.Item(
                        e.getPaymentMethod() == PaymentMethod.CREDIT ? PaymentSummary.Payment.CREDIT : PaymentSummary.Payment.PIX,
                        e.getCardId(), e.getAmountCents())).toList());
        List<CardEntry> byCard = payments.byCard().stream()
                .map(c -> new CardEntry(c.cardId(), c.cardId() == null ? null : cardNames.get(c.cardId()), c.amountCents()))
                .toList();
        List<PersonEntry> byPerson = ThirdPartySummary.of(monthExpenses.stream().filter(e -> e.getPersonId() != null)
                        .map(e -> new ThirdPartySummary.Item(e.getPersonId(), e.getCardId(), e.getAmountCents())).toList())
                .stream().map(p -> new PersonEntry(p.personId(), personNames.get(p.personId()), p.amountCents(),
                        p.byCard().stream().map(c -> new CardEntry(c.cardId(),
                                c.cardId() == null ? null : cardNames.get(c.cardId()), c.amountCents())).toList()))
                .toList();
        return new Summaries(byCategory, new PaymentEntry(payments.pixCents(), payments.creditCents()), byCard, byPerson);
    }

    private static List<Position> positions(YearMonth month, List<Expense> monthExpenses, List<Income> monthIncomes,
                                            List<Integer> closingDays, List<Card> yearCards) {
        Map<Long, Integer> dueDayByCard = yearCards.stream().collect(Collectors.toMap(Card::getId, Card::getDueDay));
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
