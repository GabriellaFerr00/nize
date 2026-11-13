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
                credit(10L, 1L, 12_000), credit(10L, 1L, 3_000), credit(10L, 2L, 5_000),
                credit(20L, 1L, 8_000), credit(20L, null, 1_500));

        List<PersonEntry> summary = ThirdPartySummary.of(items);

        assertThat(summary).containsExactly(
                new PersonEntry(10L, 20_000, List.of(new CardAmount(1L, 15_000), new CardAmount(2L, 5_000)), 0),
                // an old credit row without a card still counts for the person, but no "Sem cartão" line (REQ-037, 2026-11-09)
                new PersonEntry(20L, 9_500, List.of(new CardAmount(1L, 8_000)), 0));
        assertThat(summary.stream().mapToLong(PersonEntry::amountCents).sum()).isEqualTo(29_500);
        assertThat(summary.get(0).byCard().stream().mapToLong(CardAmount::amountCents).sum()).isEqualTo(20_000);
    }

    @Test
    void pixExpensesOfAPersonGoToItsPixLineSoCardsPlusPixMakeThePerson() {
        // REQ-038 (2026-11-12): Nubank 20000 + Pix 150000 -> person 170000
        List<Item> items = List.of(credit(10L, 1L, 20_000), pix(10L, 100_000), pix(10L, 50_000), pix(20L, 3_000));

        List<PersonEntry> summary = ThirdPartySummary.of(items);

        assertThat(summary).containsExactly(
                new PersonEntry(10L, 170_000, List.of(new CardAmount(1L, 20_000)), 150_000),
                new PersonEntry(20L, 3_000, List.of(), 3_000));
        PersonEntry first = summary.get(0);
        assertThat(first.byCard().stream().mapToLong(CardAmount::amountCents).sum() + first.pixCents())
                .isEqualTo(first.amountCents());
    }

    private static Item credit(Long person, Long card, long cents) {
        return new Item(person, false, card, cents);
    }

    private static Item pix(Long person, long cents) {
        return new Item(person, true, null, cents);
    }

    @Test
    void noThirdPartyExpensesGiveNoEntries() {
        assertThat(ThirdPartySummary.of(List.of())).isEmpty();
    }

    @Test
    void invalidItemsAreRejected() {
        assertThatThrownBy(() -> credit(null, 1L, 100)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> credit(1L, 1L, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(1L, true, 1L, 100)).isInstanceOf(IllegalArgumentException.class); // Pix has no card
    }
}
