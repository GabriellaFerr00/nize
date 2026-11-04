package br.com.nize.rules;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Third-party expenses by person, each with a breakdown by card (null = "Sem cartão") (REQ-038). Pure rule: whole
 * cents only. Σ people = third-party total; Σ cards of a person = that person's amount.
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
        for (Item item : items) {
            byPerson.computeIfAbsent(item.personId(), k -> new LinkedHashMap<>())
                    .merge(item.cardId(), item.amountCents(), Math::addExact);
        }
        return byPerson.entrySet().stream()
                .map(person -> {
                    List<CardAmount> cards = person.getValue().entrySet().stream()
                            .map(c -> new CardAmount(c.getKey(), c.getValue()))
                            .toList();
                    long amount = cards.stream().mapToLong(CardAmount::amountCents).reduce(0, Math::addExact);
                    return new PersonEntry(person.getKey(), amount, cards);
                })
                .sorted(Comparator.comparing(PersonEntry::amountCents, Comparator.reverseOrder()))
                .toList();
    }
}
