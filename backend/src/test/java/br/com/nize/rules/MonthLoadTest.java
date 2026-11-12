package br.com.nize.rules;

import br.com.nize.rules.MonthLoad.Action;
import br.com.nize.rules.MonthLoad.Decision;
import br.com.nize.rules.MonthLoad.InstallmentKey;
import br.com.nize.rules.MonthLoad.PreviousExpense;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Scenarios "Carga ao abrir um mês" and "Parcelas geradas não se repetem" (REQ-022, NFR-003). */
class MonthLoadTest {

    private static PreviousExpense fixed(String key) {
        return new PreviousExpense(key, true, null, null, null);
    }

    private static PreviousExpense oneOff(String key) {
        return new PreviousExpense(key, false, null, null, null);
    }

    private static PreviousExpense installment(String key, String group, int number, int count) {
        return new PreviousExpense(key, false, group, number, count);
    }

    private static List<Decision> load(List<PreviousExpense> previous, Set<InstallmentKey> existing, boolean auto) {
        return MonthLoad.decide(previous, existing, auto);
    }

    @Test
    void aFixedExpenseIsCopiedToTheNewMonth() {
        assertThat(load(List.of(fixed("rent")), Set.of(), true))
                .containsExactly(new Decision("rent", Action.COPY_FIXED, null));
    }

    @Test
    void aOneOffExpenseIsNotCopied() {
        assertThat(load(List.of(oneOff("coffee")), Set.of(), true)).isEmpty();
    }

    @Test
    void seventhOfTwelveCreatesTheEighth() {
        assertThat(load(List.of(installment("sofa", "g1", 7, 12)), Set.of(), true))
                .containsExactly(new Decision("sofa", Action.NEXT_INSTALLMENT, 8));
    }

    @Test
    void theLastInstallmentDoesNotCreateAnotherOne() {
        assertThat(load(List.of(installment("sofa", "g1", 12, 12)), Set.of(), true)).isEmpty();
        assertThat(load(List.of(installment("single", "g2", 1, 1)), Set.of(), true)).isEmpty();
    }

    @Test
    void anInstallmentThatAlreadyExistsIsNotCreatedAgain() {
        Set<InstallmentKey> existing = Set.of(new InstallmentKey("g1", 8));
        assertThat(load(List.of(installment("sofa", "g1", 7, 12)), existing, true)).isEmpty();
        // another group with the same number is unrelated
        assertThat(load(List.of(installment("tv", "g2", 7, 12)), existing, true))
                .containsExactly(new Decision("tv", Action.NEXT_INSTALLMENT, 8));
    }

    @Test
    void withAutomaticInstallmentsOffOnlyTheFixedExpensesAreCopied() {
        List<PreviousExpense> previous = List.of(fixed("rent"), installment("sofa", "g1", 7, 12), oneOff("coffee"));
        assertThat(load(previous, Set.of(), false)).containsExactly(new Decision("rent", Action.COPY_FIXED, null));
    }

    @Test
    void severalItemsKeepTheirOrderAndEachDecidesOnItsOwn() {
        List<PreviousExpense> previous = List.of(
                fixed("rent"), oneOff("coffee"), installment("sofa", "g1", 7, 12), installment("tv", "g2", 3, 3),
                fixed("internet"), installment("phone", "g3", 1, 10));

        assertThat(load(previous, Set.of(new InstallmentKey("g3", 2)), true)).containsExactly(
                new Decision("rent", Action.COPY_FIXED, null),
                new Decision("sofa", Action.NEXT_INSTALLMENT, 8),
                new Decision("internet", Action.COPY_FIXED, null));
    }

    @Test
    void anInstallmentWithoutAGroupCannotBeContinued() {
        // such rows should not exist (manual installments get their own group), but the rule must not guess
        assertThat(load(List.of(new PreviousExpense("legacy", false, null, 2, 5)), Set.of(), true)).isEmpty();
    }

    @Test
    void nothingToLoadGivesNothing() {
        assertThat(load(List.of(), Set.of(), true)).isEmpty();
    }

    // ---- a fixed item reaches the months already opened (REQ-054) ----

    @Test
    void aNewFixedItemGoesToEveryLaterOpenedMonthWithoutACopyInOrder() {
        List<String> opened = List.of("2026-12", "2026-09", "2026-10", "2027-01", "2026-11");

        assertThat(MonthLoad.monthsToFill("2026-10", opened, Set.of("2026-12")))
                .containsExactly("2026-11", "2027-01");
    }

    @Test
    void earlierMonthsAndTheMonthItselfNeverChange() {
        assertThat(MonthLoad.monthsToFill("2026-10", List.of("2026-08", "2026-09", "2026-10"), Set.of())).isEmpty();
    }

    @Test
    void withNoLaterMonthOpenedThereIsNothingToFill() {
        assertThat(MonthLoad.monthsToFill("2026-10", List.of(), Set.of())).isEmpty();
    }

    @Test
    void laterMonthsAreTheOnesAfterTheGivenOne() {
        assertThat(MonthLoad.isAfter("2026-11", "2026-10")).isTrue();
        assertThat(MonthLoad.isAfter("2027-01", "2026-12")).isTrue();
        assertThat(MonthLoad.isAfter("2026-10", "2026-10")).isFalse();
        assertThat(MonthLoad.isAfter("2026-09", "2026-10")).isFalse();
    }
}
