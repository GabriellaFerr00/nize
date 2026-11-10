package br.com.nize.rules;

import br.com.nize.rules.ClosingPositions.Expense;
import br.com.nize.rules.ClosingPositions.Payment;
import br.com.nize.rules.ClosingPositions.Position;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Features "Posição por quinzena" and "Pix e crédito na posição por quinzena" from the PDF, run with the default
 * closing days 15 and 31, plus the configurable closings (REQ-030, REQ-031, REQ-032, REQ-048, NFR-003).
 * Adapted on purpose (REQ-030, user decisions 2026-11-04 and 2026-11-09): each position is only the sum of the
 * expenses inside its own interval between closings (1–15, 16–end); incomes do not count, nothing is carried from the
 * previous intervals, and the last interval runs to the end of the month.
 * "Marcar como pago não altera" (REQ-033) holds by construction: "paid" is not an input of this rule; the API test of
 * TASK-034 checks it end to end.
 */
class ClosingPositionsTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final List<Integer> DEFAULT_DAYS = List.of(15, 31);

    private static LocalDate april(int day) {
        return APRIL.atDay(day);
    }

    private static Expense pix(long cents, LocalDate date) {
        return new Expense(cents, Payment.PIX, date, null);
    }

    private static Expense credit(long cents, LocalDate purchase, Integer cardDueDay) {
        return new Expense(cents, Payment.CREDIT, purchase, cardDueDay);
    }

    // ---- Soma dos gastos por intervalo ----

    @Test
    void theFirstIntervalIsTheSumOfItsExpenses() {
        // Dado gastos de 120000 centavos até o dia 15, em dois lançamentos
        List<Expense> expenses = List.of(pix(70_000, april(3)), pix(50_000, april(10)));

        List<Position> positions = ClosingPositions.of(APRIL, expenses, DEFAULT_DAYS);

        // Então o intervalo 1–15 soma 120000 centavos
        assertThat(positions.get(0)).isEqualTo(new Position(1, 15, 120_000));
    }

    @Test
    void theSecondIntervalDoesNotCarryTheFirstOne() {
        // Dado gastos de 120000 do dia 1 ao 15 e de 30000 do dia 16 ao 30
        List<Expense> expenses = List.of(pix(120_000, april(10)), pix(30_000, april(20)));

        List<Position> positions = ClosingPositions.of(APRIL, expenses, DEFAULT_DAYS);

        // Então o 2º intervalo soma 30000 (não 150000); dia 31 em abril = dia 30
        assertThat(positions).containsExactly(new Position(1, 15, 120_000), new Position(16, 30, 30_000));
    }

    @Test
    void anIntervalWithoutExpensesIsZero() {
        List<Position> positions = ClosingPositions.of(APRIL, List.of(pix(9_000, april(2))), DEFAULT_DAYS);

        assertThat(positions).containsExactly(new Position(1, 15, 9_000), new Position(16, 30, 0));
    }

    // ---- Pix e crédito ----

    @Test
    void aPixExpenseWeighsOnThePaymentDate() {
        // Dado um gasto de 20000 centavos no Pix pago em 12/04
        List<Position> positions = ClosingPositions.of(APRIL, List.of(pix(20_000, april(12))), DEFAULT_DAYS);

        // Então ele entra na soma do intervalo 1–15
        assertThat(positions.get(0)).isEqualTo(new Position(1, 15, 20_000));
    }

    @Test
    void aCreditExpenseWeighsOnTheCardDueDayNotOnThePurchaseDate() {
        // Dado um cartão com vencimento no dia 10 e uma compra de 50000 centavos no crédito, lançada em abril
        Expense purchase = credit(50_000, april(28), 10);

        List<Position> positions = ClosingPositions.of(APRIL, List.of(purchase), DEFAULT_DAYS);

        // Então o gasto entra no intervalo 1–15, e não na data da compra
        assertThat(ClosingPositions.effectiveDate(purchase, APRIL)).isEqualTo(april(10));
        assertThat(positions).containsExactly(new Position(1, 15, 50_000), new Position(16, 30, 0));
    }

    @Test
    void creditWithoutACardUsesTheExpenseDate() {
        Expense purchase = credit(7_000, april(20), null);

        assertThat(ClosingPositions.effectiveDate(purchase, APRIL)).isEqualTo(april(20));
        assertThat(ClosingPositions.of(APRIL, List.of(purchase), DEFAULT_DAYS))
                .containsExactly(new Position(1, 15, 0), new Position(16, 30, 7_000));
    }

    @Test
    void aDueDayPastTheEndOfTheMonthFallsOnTheLastDay() {
        YearMonth february = YearMonth.of(2026, 2);
        Expense purchase = credit(9_900, february.atDay(3), 31);

        assertThat(ClosingPositions.effectiveDate(purchase, february)).isEqualTo(february.atDay(28));
    }

    // ---- Configurable closings (REQ-048) ----

    @Test
    void anyClosingDaysGiveOneIntervalEachInAscendingOrderAndTheLastRunsToTheEnd() {
        List<Expense> expenses = List.of(pix(10_000, april(5)), pix(20_000, april(11)), pix(30_000, april(20)));

        List<Position> positions = ClosingPositions.of(APRIL, expenses, List.of(20, 5, 10));

        // 1–5, 6–10 and 11–30: the last closing (20) runs to the end of the month
        assertThat(positions).containsExactly(
                new Position(1, 5, 10_000), new Position(6, 10, 0), new Position(11, 30, 50_000));
    }

    @Test
    void thirtyAndThirtyOneInFebruaryBecomeASinglePositionOnTheLastDay() {
        YearMonth february = YearMonth.of(2026, 2);

        assertThat(ClosingPositions.of(february, List.of(pix(10_000, february.atDay(28))), List.of(30, 31)))
                .containsExactly(new Position(1, 28, 10_000));
    }

    @Test
    void aLeapFebruaryEndsOnThe29th() {
        YearMonth leapFebruary = YearMonth.of(2028, 2);

        assertThat(ClosingPositions.of(leapFebruary, List.of(pix(10_000, leapFebruary.atDay(29))), List.of(15, 31)))
                .containsExactly(new Position(1, 15, 0), new Position(16, 29, 10_000));
    }

    @Test
    void aDayInsideTheMonthStaysAsChosen() {
        // In a 31-day month, closings on the 30th and 31st give a one-day last interval.
        YearMonth may = YearMonth.of(2026, 5);

        assertThat(ClosingPositions.of(may, List.of(pix(5_000, may.atDay(31))), Set.of(30, 31)))
                .containsExactly(new Position(1, 30, 0), new Position(31, 31, 5_000));
    }

    @Test
    void entriesAfterTheLastClosingDayFallInTheLastInterval() {
        // Fechamentos 10 e 20: o que cai depois do dia 20 entra no intervalo 11–fim do mês
        List<Expense> expenses = List.of(pix(4_000, april(10)), pix(5_000, april(25)));

        assertThat(ClosingPositions.of(APRIL, expenses, List.of(10, 20)))
                .containsExactly(new Position(1, 10, 4_000), new Position(11, 30, 5_000));
    }

    @Test
    void aSingleClosingDayCoversTheWholeMonth() {
        assertThat(ClosingPositions.of(APRIL, List.of(pix(1_000, april(29))), List.of(10)))
                .containsExactly(new Position(1, 30, 1_000));
    }

    @Test
    void noClosingDaysGiveNoPositions() {
        assertThat(ClosingPositions.of(APRIL, List.of(pix(1_000, april(1))), List.of())).isEmpty();
    }

    @Test
    void closingDaysMustBeBetween1And31() {
        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(), List.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(), List.of(32)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Input checks ----

    @Test
    void datesMustBeInsideTheMonth() {
        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(pix(1_000, LocalDate.of(2026, 5, 1))), DEFAULT_DAYS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountsMustBePositiveAndFieldsPresent() {
        assertThatThrownBy(() -> pix(0, april(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expense(100, null, april(1), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> credit(100, april(1), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expense(100, Payment.PIX, april(1), 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOverflowingSumFailsInsteadOfWrappingAround() {
        List<Expense> expenses = List.of(pix(Long.MAX_VALUE, april(1)), pix(1, april(2)));

        assertThatThrownBy(() -> ClosingPositions.of(APRIL, expenses, DEFAULT_DAYS))
                .isInstanceOf(ArithmeticException.class);
    }
}
