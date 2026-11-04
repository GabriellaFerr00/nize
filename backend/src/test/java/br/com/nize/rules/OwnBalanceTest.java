package br.com.nize.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "Gastos de terceiros" from the PDF: own balance leaves out third-party expenses and reimbursements (REQ-034, NFR-003). */
class OwnBalanceTest {

    @Test
    void ownBalanceIsTheBalanceWithoutThirdPartyExpensesAndReimbursements() {
        // Saldo de R$ 500,00, gastos de terceiros de R$ 200,00 e reembolsos de R$ 190,00 → saldo próprio R$ 510,00
        long ownBalance = OwnBalance.of(50_000, List.of(12_000L, 8_000L), List.of(19_000L));

        assertThat(ownBalance).isEqualTo(51_000);
    }

    @Test
    void withoutThirdPartiesItIsTheMonthBalance() {
        assertThat(OwnBalance.of(68_070, List.of(), List.of())).isEqualTo(68_070);
    }

    @Test
    void aNegativeBalanceStaysNegativeWhenNothingIsOwed() {
        assertThat(OwnBalance.of(-30_000, List.of(), List.of(5_000L))).isEqualTo(-35_000);
    }

    @Test
    void aReimbursementNotYetMatchedByExpensesLowersTheOwnBalance() {
        // Money returned this month for a purchase of an earlier month: it is not the user's own money.
        assertThat(OwnBalance.of(10_000, List.of(), List.of(10_000L))).isZero();
    }

    @Test
    void amountsMustBePositive() {
        assertThatThrownBy(() -> OwnBalance.of(0, List.of(0L), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OwnBalance.of(0, List.of(), List.of(-1L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOverflowFailsInsteadOfWrappingAround() {
        assertThatThrownBy(() -> OwnBalance.of(Long.MAX_VALUE, List.of(1L), List.of()))
                .isInstanceOf(ArithmeticException.class);
    }
}
