package br.com.nize.rules;

import br.com.nize.rules.CategorySummary.Entry;
import br.com.nize.rules.CategorySummary.Item;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Feature "Resumo por categoria" from the PDF (REQ-035, NFR-003). */
class CategorySummaryTest {

    @Test
    void percentageOverTheTotalExpenses() {
        // Dado que "Despesas eventuais" gastou 141814 centavos e o total de gastos do mês é 594288 centavos
        List<Item> items = List.of(new Item(7L, 100_000), new Item(7L, 41_814), new Item(3L, 452_474));

        List<Entry> summary = CategorySummary.of(items);

        // Então a porcentagem é 23,86%
        assertThat(summary).contains(new Entry(7L, 141_814, new BigDecimal("23.86")));
    }

    @Test
    void categoriesAddUpToTheTotalExpenses() {
        // Dado todos os lançamentos do mês, então a soma dos gastos por categoria é igual ao total de gastos
        Random random = new Random(42);
        List<Item> items = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < 500; i++) {
            long cents = 1 + random.nextInt(200_000);
            items.add(new Item((long) random.nextInt(15), cents));
            total += cents;
        }

        List<Entry> summary = CategorySummary.of(items);

        assertThat(summary.stream().mapToLong(Entry::amountCents).sum()).isEqualTo(total);
        assertThat(summary).extracting(Entry::categoryId).doesNotHaveDuplicates();
    }

    @Test
    void entriesComeFromTheLargestAmountAndPercentagesHaveTwoDecimals() {
        List<Entry> summary = CategorySummary.of(List.of(new Item(1L, 1), new Item(2L, 2), new Item(3L, 5)));

        assertThat(summary).extracting(Entry::categoryId).containsExactly(3L, 2L, 1L);
        assertThat(summary).extracting(Entry::percent)
                .containsExactly(new BigDecimal("62.50"), new BigDecimal("25.00"), new BigDecimal("12.50"));
    }

    @Test
    void halfARoundsUp() {
        // 1 of 8 = 12.5% exactly; 1 of 3 = 33.333...% → 33.33; 2 of 3 = 66.666...% → 66.67; 1 of 16 = 6.25%
        assertThat(CategorySummary.percent(1, 3)).isEqualByComparingTo("33.33");
        assertThat(CategorySummary.percent(2, 3)).isEqualByComparingTo("66.67");
        assertThat(CategorySummary.percent(1, 16)).isEqualByComparingTo("6.25");
        assertThat(CategorySummary.percent(1, 200_000)).isEqualByComparingTo("0.00");
        assertThat(CategorySummary.percent(1, 20_000)).isEqualByComparingTo("0.01");
    }

    @Test
    void anEmptyMonthHasNoEntries() {
        assertThat(CategorySummary.of(List.of())).isEmpty();
    }

    @Test
    void amountsMustBePositive() {
        assertThatThrownBy(() -> new Item(1L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(null, 10)).isInstanceOf(NullPointerException.class);
    }
}
