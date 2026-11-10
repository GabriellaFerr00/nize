package br.com.nize.rules;

import br.com.nize.rules.ThirdPartySummary.CardAmount;
import br.com.nize.rules.ThirdPartySummary.Item;
import br.com.nize.rules.ThirdPartySummary.PersonEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "Gastos de terceiros" by person and by card (REQ-038, NFR-003). */
class ThirdPartySummaryTest {

    @Test
    void peopleAddUpToTheThirdPartyTotalWithACardBreakdown() {
        List<Item> items = List.of(
                new Item(10L, 1L, 12_000), new Item(10L, 1L, 3_000), new Item(10L, 2L, 5_000),
                new Item(20L, 1L, 8_000), new Item(20L, null, 1_500));

        List<PersonEntry> summary = ThirdPartySummary.of(items);

        assertThat(summary).containsExactly(
                new PersonEntry(10L, 20_000, List.of(new CardAmount(1L, 15_000), new CardAmount(2L, 5_000))),
                // an old row without a card still counts for the person, but no "Sem cartão" line (REQ-037, 2026-11-09)
                new PersonEntry(20L, 9_500, List.of(new CardAmount(1L, 8_000))));
        assertThat(summary.stream().mapToLong(PersonEntry::amountCents).sum()).isEqualTo(29_500);
        assertThat(summary.get(0).byCard().stream().mapToLong(CardAmount::amountCents).sum()).isEqualTo(20_000);
    }

    @Test
    void noThirdPartyExpensesGiveNoEntries() {
        assertThat(ThirdPartySummary.of(List.of())).isEmpty();
    }

    @Test
    void invalidItemsAreRejected() {
        assertThatThrownBy(() -> new Item(null, 1L, 100)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Item(1L, 1L, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
