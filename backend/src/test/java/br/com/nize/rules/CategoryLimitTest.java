package br.com.nize.rules;

import br.com.nize.rules.CategoryLimit.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Optional monthly limit per category (REQ-051): warn near and over the limit, never block. */
class CategoryLimitTest {

    @Test
    void aCategoryWithoutALimitHasNoWarning() {
        assertThat(CategoryLimit.of(999_999, null)).isEqualTo(Status.NONE);
    }

    @Test
    void below80PercentIsFine() {
        assertThat(CategoryLimit.of(79_999, 100_000L)).isEqualTo(Status.UNDER);
        assertThat(CategoryLimit.of(0, 100_000L)).isEqualTo(Status.UNDER);
    }

    @Test
    void from80PercentUpToTheLimitIsNear() {
        // Dado um limite de 100000 e gastos de 80000 no mês: perto do limite
        assertThat(CategoryLimit.of(80_000, 100_000L)).isEqualTo(Status.NEAR);
        assertThat(CategoryLimit.of(100_000, 100_000L)).isEqualTo(Status.NEAR);
    }

    @Test
    void aboveTheLimitIsOver() {
        // 100001 → o limite passou
        assertThat(CategoryLimit.of(100_001, 100_000L)).isEqualTo(Status.OVER);
    }

    @Test
    void eightyPercentIsExactWithoutRounding() {
        // 80% of 333 cents is 266.4: 266 is under, 267 is near
        assertThat(CategoryLimit.of(266, 333L)).isEqualTo(Status.UNDER);
        assertThat(CategoryLimit.of(267, 333L)).isEqualTo(Status.NEAR);
    }

    @Test
    void hugeValuesDoNotOverflow() {
        assertThat(CategoryLimit.of(999_999_999_999L * 12, 999_999_999_999L)).isEqualTo(Status.OVER);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThatThrownBy(() -> CategoryLimit.of(-1, 100L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CategoryLimit.of(1, 0L)).isInstanceOf(IllegalArgumentException.class);
    }
}
