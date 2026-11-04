package br.com.nize.rules;

import br.com.nize.rules.MonthTotals.Expense;
import br.com.nize.rules.MonthTotals.Kind;
import br.com.nize.rules.MonthTotals.Totals;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Feature "Totais do mês" from the PDF (REQ-027, REQ-028, REQ-029, NFR-003). */
class MonthTotalsTest {

    // ---- Scenarios from the PDF ----

    @Test
    void totalExpensesAddsFixedAndCard() {
        // Dado fixos que somam 99399 centavos e lançamentos de cartão que somam 494889 centavos
        List<Expense> expenses = List.of(
                new Expense(Kind.FIXED, 60_000), new Expense(Kind.FIXED, 39_399),
                new Expense(Kind.CARD, 400_000), new Expense(Kind.CARD, 94_889));

        Totals totals = MonthTotals.of(expenses, List.of());

        // Então o total de gastos do mês é 594288 centavos
        assertThat(totals.totalExpensesCents()).isEqualTo(594_288);
        assertThat(totals.totalFixedCents()).isEqualTo(99_399);
    }

    @Test
    void balanceIsIncomeMinusExpenses() {
        // Dado entradas que somam 662358 centavos e total de gastos de 594288 centavos
        List<Expense> expenses = List.of(new Expense(Kind.FIXED, 99_399), new Expense(Kind.CARD, 494_889));
        List<Long> incomes = List.of(500_000L, 162_358L);

        Totals totals = MonthTotals.of(expenses, incomes);

        // Então o saldo do mês é 68070 centavos
        assertThat(totals.totalIncomeCents()).isEqualTo(662_358);
        assertThat(totals.totalExpensesCents()).isEqualTo(594_288);
        assertThat(totals.balanceCents()).isEqualTo(68_070);
    }

    // ---- Edges ----

    @Test
    void totalFixedCountsOnlyFixedExpenses() {
        // A fixed expense paid by credit card (subscription) is still FIXED (design.md).
        Totals totals = MonthTotals.of(
                List.of(new Expense(Kind.FIXED, 5_590), new Expense(Kind.CARD, 12_000), new Expense(Kind.CARD, 3_000)),
                List.of());

        assertThat(totals.totalFixedCents()).isEqualTo(5_590);
        assertThat(totals.totalExpensesCents()).isEqualTo(20_590);
    }

    @Test
    void anEmptyMonthIsAllZero() {
        assertThat(MonthTotals.of(List.of(), List.of())).isEqualTo(new Totals(0, 0, 0, 0));
    }

    @Test
    void spendingMoreThanTheIncomeGivesANegativeBalance() {
        Totals totals = MonthTotals.of(List.of(new Expense(Kind.CARD, 380_000)), List.of(350_000L));

        assertThat(totals.balanceCents()).isEqualTo(-30_000);
    }

    @Test
    void onlyIncomeGivesABalanceEqualToTheIncome() {
        Totals totals = MonthTotals.of(List.of(), List.of(250_000L));

        assertThat(totals.balanceCents()).isEqualTo(250_000);
        assertThat(totals.totalExpensesCents()).isZero();
    }

    @Test
    void anOverflowingSumFailsInsteadOfWrappingAround() {
        List<Expense> expenses = List.of(new Expense(Kind.CARD, Long.MAX_VALUE), new Expense(Kind.CARD, 1));

        assertThatThrownBy(() -> MonthTotals.of(expenses, List.of())).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void amountsMustBePositive() {
        assertThatThrownBy(() -> new Expense(Kind.FIXED, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expense(Kind.CARD, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MonthTotals.of(List.of(), List.of(0L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theKindIsRequired() {
        assertThatThrownBy(() -> new Expense(null, 100)).isInstanceOf(NullPointerException.class);
    }
}
