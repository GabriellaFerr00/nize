package br.com.nize.rules;

import br.com.nize.rules.ClosingPositions.Expense;
import br.com.nize.rules.ClosingPositions.Income;
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

    // ---- Posição por quinzena ----

    @Test
    void whatIsLeftOrMissingUntilThe15th() {
        // Dado entradas de 300000 centavos e gastos de 120000 centavos até o dia 15
        List<Income> incomes = List.of(new Income(300_000, april(5)));
        List<Expense> expenses = List.of(pix(120_000, april(10)));

        List<Position> positions = ClosingPositions.of(APRIL, expenses, incomes, DEFAULT_DAYS);

        // Então a sobra até o dia 15 é 180000 centavos
        assertThat(positions.get(0)).isEqualTo(new Position(15, 180_000));
    }

    @Test
    void positionUntilTheEndOfTheMonth() {
        // Dado entradas de 350000 centavos e gastos de 380000 centavos até o fim do mês
        List<Income> incomes = List.of(new Income(300_000, april(5)), new Income(50_000, april(25)));
        List<Expense> expenses = List.of(pix(200_000, april(8)), pix(180_000, april(30)));

        List<Position> positions = ClosingPositions.of(APRIL, expenses, incomes, DEFAULT_DAYS);

        // Então a posição até o último dia é -30000 centavos (faltam R$ 300,00); dia 31 em abril = dia 30
        assertThat(positions).containsExactly(new Position(15, 100_000), new Position(30, -30_000));
    }

    // ---- Pix e crédito ----

    @Test
    void aPixExpenseWeighsOnThePaymentDate() {
        // Dado um gasto de 20000 centavos no Pix pago em 12/04
        List<Position> positions = ClosingPositions.of(APRIL, List.of(pix(20_000, april(12))), List.of(), DEFAULT_DAYS);

        // Então ele pesa na posição até o dia 15 de abril
        assertThat(positions.get(0)).isEqualTo(new Position(15, -20_000));
    }

    @Test
    void aCreditExpenseWeighsOnTheCardDueDayNotOnThePurchaseDate() {
        // Dado um cartão com vencimento no dia 10 e uma compra de 50000 centavos no crédito, lançada em abril
        Expense purchase = credit(50_000, april(28), 10);

        List<Position> positions = ClosingPositions.of(APRIL, List.of(purchase), List.of(), DEFAULT_DAYS);

        // Então o gasto pesa na posição até o dia 15 de abril, e não na data da compra
        assertThat(ClosingPositions.effectiveDate(purchase, APRIL)).isEqualTo(april(10));
        assertThat(positions.get(0)).isEqualTo(new Position(15, -50_000));
    }

    @Test
    void creditWithoutACardUsesTheExpenseDate() {
        Expense purchase = credit(7_000, april(20), null);

        assertThat(ClosingPositions.effectiveDate(purchase, APRIL)).isEqualTo(april(20));
        assertThat(ClosingPositions.of(APRIL, List.of(purchase), List.of(), DEFAULT_DAYS))
                .containsExactly(new Position(15, 0), new Position(30, -7_000));
    }

    @Test
    void aDueDayPastTheEndOfTheMonthFallsOnTheLastDay() {
        YearMonth february = YearMonth.of(2026, 2);
        Expense purchase = credit(9_900, february.atDay(3), 31);

        assertThat(ClosingPositions.effectiveDate(purchase, february)).isEqualTo(february.atDay(28));
    }

    // ---- Configurable closings (REQ-048) ----

    @Test
    void anyClosingDaysGiveOnePositionEachInAscendingOrder() {
        List<Income> incomes = List.of(new Income(100_000, april(1)));
        List<Expense> expenses = List.of(pix(10_000, april(5)), pix(20_000, april(11)), pix(30_000, april(20)));

        List<Position> positions = ClosingPositions.of(APRIL, expenses, incomes, List.of(20, 5, 10));

        assertThat(positions).containsExactly(
                new Position(5, 90_000), new Position(10, 90_000), new Position(20, 40_000));
    }

    @Test
    void thirtyAndThirtyOneInFebruaryBecomeASinglePositionOnTheLastDay() {
        YearMonth february = YearMonth.of(2026, 2);
        List<Income> incomes = List.of(new Income(10_000, february.atDay(28)));

        assertThat(ClosingPositions.of(february, List.of(), incomes, List.of(30, 31)))
                .containsExactly(new Position(28, 10_000));
    }

    @Test
    void aLeapFebruaryEndsOnThe29th() {
        YearMonth leapFebruary = YearMonth.of(2028, 2);
        List<Income> incomes = List.of(new Income(10_000, leapFebruary.atDay(29)));

        assertThat(ClosingPositions.of(leapFebruary, List.of(), incomes, List.of(15, 31)))
                .containsExactly(new Position(15, 0), new Position(29, 10_000));
    }

    @Test
    void aDayInsideTheMonthStaysAsChosen() {
        // In a 31-day month, closing on the 30th leaves the 31st out.
        YearMonth may = YearMonth.of(2026, 5);
        List<Income> incomes = List.of(new Income(5_000, may.atDay(31)));

        assertThat(ClosingPositions.of(may, List.of(), incomes, Set.of(30, 31)))
                .containsExactly(new Position(30, 0), new Position(31, 5_000));
    }

    @Test
    void noClosingDaysGiveNoPositions() {
        assertThat(ClosingPositions.of(APRIL, List.of(pix(1_000, april(1))), List.of(), List.of())).isEmpty();
    }

    @Test
    void closingDaysMustBeBetween1And31() {
        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(), List.of(), List.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(), List.of(), List.of(32)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Input checks ----

    @Test
    void datesMustBeInsideTheMonth() {
        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(pix(1_000, LocalDate.of(2026, 5, 1))), List.of(), DEFAULT_DAYS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(), List.of(new Income(1_000, LocalDate.of(2026, 3, 31))), DEFAULT_DAYS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountsMustBePositiveAndFieldsPresent() {
        assertThatThrownBy(() -> pix(0, april(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Income(-5, april(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expense(100, null, april(1), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Income(100, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> credit(100, april(1), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expense(100, Payment.PIX, april(1), 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOverflowingSumFailsInsteadOfWrappingAround() {
        List<Income> incomes = List.of(new Income(Long.MAX_VALUE, april(1)), new Income(1, april(2)));

        assertThatThrownBy(() -> ClosingPositions.of(APRIL, List.of(), incomes, DEFAULT_DAYS))
                .isInstanceOf(ArithmeticException.class);
    }
}
