package br.com.nize.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Scenarios "Compra dividida sem perder centavos" and "Parcelas automáticas" (REQ-018, REQ-020, NFR-003). */
class InstallmentsTest {

    // ---- splitTotal: R$ 100,00 em 3x ----

    @Test
    void oneHundredReaisInThreeInstallmentsLeavesTheDifferenceInTheLast() {
        assertThat(Installments.splitTotal(10_000, 3)).containsExactly(3333L, 3333L, 3334L);
    }

    @Test
    void anExactDivisionGivesEqualInstallments() {
        assertThat(Installments.splitTotal(12_000, 4)).containsExactly(3000L, 3000L, 3000L, 3000L);
    }

    @Test
    void withoutInstallmentsItIsASingleExpenseOfTheWholeValue() {
        assertThat(Installments.splitTotal(47_500, 1)).containsExactly(47_500L);
    }

    @Test
    void theSumIsAlwaysTheTotalAndTheShapeIsAlwaysTheSame() {
        for (long total = 1; total <= 3_000; total++) {
            for (int count = 1; count <= Math.min(total, 48); count++) {
                List<Long> parts = Installments.splitTotal(total, count);

                assertThat(parts).hasSize(count);
                assertThat(parts.stream().mapToLong(Long::longValue).sum())
                        .as("sum of %d in %d", total, count).isEqualTo(total);
                assertThat(parts).allSatisfy(part -> assertThat(part).isPositive());
                long base = parts.get(0);
                assertThat(parts.subList(0, count - 1)).allSatisfy(part -> assertThat(part).isEqualTo(base));
                long last = parts.get(count - 1);
                assertThat(last).isGreaterThanOrEqualTo(base).isLessThan(base + count);
            }
        }
    }

    @Test
    void hugeValuesDoNotOverflow() {
        List<Long> parts = Installments.splitTotal(Long.MAX_VALUE, 7);
        assertThat(parts.stream().mapToLong(Long::longValue).sum()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void everyInstallmentNeedsAtLeastOneCent() {
        assertThat(Installments.splitTotal(3, 3)).containsExactly(1L, 1L, 1L);
        assertThatThrownBy(() -> Installments.splitTotal(2, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> Installments.splitTotal(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.splitTotal(-100, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.splitTotal(100, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.splitTotal(100, -2)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- repeatValue: 4x de R$ 50,00 ----

    @Test
    void fourInstallmentsOfFiftyReaisAreFourTimesFiveThousandCents() {
        assertThat(Installments.repeatValue(5_000, 4)).containsExactly(5_000L, 5_000L, 5_000L, 5_000L);
    }

    @Test
    void repeatingOnceIsASingleExpense() {
        assertThat(Installments.repeatValue(12_345, 1)).containsExactly(12_345L);
    }

    @Test
    void repeatingRejectsInvalidArguments() {
        assertThatThrownBy(() -> Installments.repeatValue(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.repeatValue(-1, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.repeatValue(100, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTotalOfRepeatedInstallmentsFollowsFromTheInstallmentValue() {
        assertThat(Installments.repeatValue(3_333, 3).stream().mapToLong(Long::longValue).sum()).isEqualTo(9_999L);
    }
}
