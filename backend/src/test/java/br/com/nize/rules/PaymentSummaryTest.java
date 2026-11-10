package br.com.nize.rules;

import br.com.nize.rules.PaymentSummary.CardEntry;
import br.com.nize.rules.PaymentSummary.Item;
import br.com.nize.rules.PaymentSummary.Payment;
import br.com.nize.rules.PaymentSummary.Summary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Features "Resumo por forma de pagamento" and "Resumo por cartão" (REQ-036, REQ-037, NFR-003). */
class PaymentSummaryTest {

    private static final List<Item> MONTH = List.of(
            new Item(Payment.PIX, null, 99_399),
            new Item(Payment.CREDIT, 1L, 300_000),
            new Item(Payment.CREDIT, 2L, 71_990),
            new Item(Payment.CREDIT, 1L, 75_500),
            new Item(Payment.CREDIT, null, 47_500));

    @Test
    void paymentMethodsAddUpToTheTotalExpenses() {
        Summary summary = PaymentSummary.of(MONTH);

        assertThat(summary.pixCents()).isEqualTo(99_399);
        assertThat(summary.creditCents()).isEqualTo(494_990);
        assertThat(summary.pixCents() + summary.creditCents()).isEqualTo(594_389);
    }

    @Test
    void thereIsNoSemCartaoGroup() {
        // Adapted (REQ-037, user decision 2026-11-09): credit always has a card; an old credit row without one still
        // counts in the credit total, but no "Sem cartão" group is listed
        Summary summary = PaymentSummary.of(MONTH);

        assertThat(summary.byCard()).containsExactly(new CardEntry(1L, 375_500), new CardEntry(2L, 71_990));
        assertThat(summary.byCard()).noneMatch(c -> c.cardId() == null);
        assertThat(summary.creditCents()).isEqualTo(494_990);
    }

    @Test
    void withEveryCreditOnACardTheCardsAddUpToTheCreditTotal() {
        Summary summary = PaymentSummary.of(MONTH.stream().filter(i -> i.payment() == Payment.PIX || i.cardId() != null).toList());

        assertThat(summary.byCard().stream().mapToLong(CardEntry::amountCents).sum()).isEqualTo(summary.creditCents());
    }

    @Test
    void cardsComeFromTheLargestAmount() {
        Summary summary = PaymentSummary.of(List.of(
                new Item(Payment.CREDIT, null, 900_000), new Item(Payment.CREDIT, 2L, 100), new Item(Payment.CREDIT, 1L, 5_000)));

        assertThat(summary.byCard()).containsExactly(new CardEntry(1L, 5_000), new CardEntry(2L, 100));
    }

    @Test
    void pixNeverEntersTheCardSummary() {
        Summary summary = PaymentSummary.of(List.of(new Item(Payment.PIX, null, 10_000)));

        assertThat(summary.byCard()).isEmpty();
        assertThat(summary.creditCents()).isZero();
    }

    @Test
    void anEmptyMonthIsAllZero() {
        assertThat(PaymentSummary.of(List.of())).isEqualTo(new Summary(0, 0, List.of()));
    }

    @Test
    void invalidItemsAreRejected() {
        assertThatThrownBy(() -> new Item(Payment.PIX, 1L, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(Payment.CREDIT, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(null, null, 100)).isInstanceOf(NullPointerException.class);
    }
}
