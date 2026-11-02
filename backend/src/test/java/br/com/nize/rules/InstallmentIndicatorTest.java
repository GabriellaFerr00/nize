package br.com.nize.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The "N/M" label of an installment (REQ-021, NFR-003). */
class InstallmentIndicatorTest {

    @Test
    void seventhOfTwelveIsSevenSlashTwelve() {
        assertThat(InstallmentIndicator.format(7, 12)).isEqualTo("7/12");
    }

    @Test
    void firstAndLastInstallments() {
        assertThat(InstallmentIndicator.format(1, 3)).isEqualTo("1/3");
        assertThat(InstallmentIndicator.format(12, 12)).isEqualTo("12/12");
    }

    @Test
    void anExpenseWithoutInstallmentsHasNoIndicator() {
        assertThat(InstallmentIndicator.format(null, null)).isEmpty();
    }

    @Test
    void inconsistentValuesAreRejected() {
        assertThatThrownBy(() -> InstallmentIndicator.format(1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentIndicator.format(null, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentIndicator.format(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentIndicator.format(4, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentIndicator.format(-1, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
