package br.com.nize.rules;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Third-party expenses by person, each with a breakdown by card (REQ-038). Pure rule: whole cents only.
 * Σ people = third-party total. An old row without a card counts in the person's amount but has no card line
 * (no "Sem cartão", REQ-037 2026-11-09).
 */
public final class ThirdPartySummary {

    private ThirdPartySummary() {
    }

    public record Item(Long personId, Long cardId, long amountCents) {
        public Item {
            Objects.requireNonNull(personId, "personId");
            if (amountCents <= 0) {
                throw new IllegalArgumentException("amount must be positive: " + amountCents);
            }
        }
    }

    public record CardAmount(Long cardId, long amountCents) {
    }

    /** People in order of first appearance; cards in order of first appearance within the person. */
    public record PersonEntry(Long personId, long amountCents, List<CardAmount> byCard) {
    }

    public static List<PersonEntry> of(List<Item> items) {
        Map<Long, Map<Long, Long>> byPerson = new LinkedHashMap<>();
        Map<Long, Long> totals = new LinkedHashMap<>();
        for (Item item : items) {
            Map<Long, Long> cards = byPerson.computeIfAbsent(item.personId(), k -> new LinkedHashMap<>());
            if (item.cardId() != null) {
                cards.merge(item.cardId(), item.amountCents(), Math::addExact);
            }
            totals.merge(item.personId(), item.amountCents(), Math::addExact);
        }
        return byPerson.entrySet().stream()
                .map(person -> new PersonEntry(person.getKey(), totals.get(person.getKey()),
                        person.getValue().entrySet().stream().map(c -> new CardAmount(c.getKey(), c.getValue())).toList()))
                .sorted(Comparator.comparing(PersonEntry::amountCents, Comparator.reverseOrder()))
                .toList();
    }
}
