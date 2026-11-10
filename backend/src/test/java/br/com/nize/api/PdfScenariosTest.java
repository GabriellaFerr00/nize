package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every Gherkin scenario of the PDF "Nize — Funcionalidades e escopo do MVP web", end to end through the API, with the
 * PDF's own numbers (TASK-046, NFR-003). Each test is named after its scenario ({@code @DisplayName}); the rules
 * behind them are also tested on their own in {@code br.com.nize.rules} (written before the code). The mapping
 * scenario → tests is in {@code docs/cobertura-cenarios.md}.
 * <p>
 * Adapted on purpose (requirements.md wins over the PDF): positions use the closing days (REQ-048, default 15 and 31,
 * where 31 is the last day of the month) instead of the fixed days 15 and 30, and each position covers only its own
 * interval between closings, not the running total, and is only the sum of the expenses in it: incomes do not count
 * (REQ-030, user decisions 2026-11-04 and 2026-11-09).
 */
class PdfScenariosTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final YearMonth MARCH = YearMonth.of(YEAR, 3);
    private static final YearMonth APRIL = YearMonth.of(YEAR, 4);
    private static final YearMonth MAY = YearMonth.of(YEAR, 5);
    private static final YearMonth JUNE = YearMonth.of(YEAR, 6);
    private static final YearMonth JULY = YearMonth.of(YEAR, 7);

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    // ---- helpers ----

    private long categoryId(String name) throws Exception {
        String body = as(userA, get("/api/years/" + YEAR + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Creates an expense of user A; {@code extra} is appended to the JSON body (e.g. {@code ,"cardId":1}). */
    private ResultActions expense(YearMonth month, String category, long cents, String type, int day, String extra)
            throws Exception {
        return as(userA, post("/api/months/" + month + "/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(withCard(userA, month, "{\"name\":\"Gasto\",\"categoryId\":%d,\"amountCents\":%d,\"type\":\"%s\",\"date\":\"%s\"%s}"
                        .formatted(categoryId(category), cents, type, month.atDay(day), extra))));
    }

    private long fixed(YearMonth month, long cents, int day) throws Exception {
        return idOf(expense(month, "Contas", cents, "FIXED", day, ""));
    }

    private long card(YearMonth month, long cents, int day) throws Exception {
        return idOf(expense(month, "Mercado", cents, "CARD", day, ""));
    }

    private void income(YearMonth month, long cents, int day) throws Exception {
        idOf(as(userA, post("/api/months/" + month + "/incomes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salário\",\"amountCents\":%d,\"date\":\"%s\"}".formatted(cents, month.atDay(day)))));
    }

    private long createCard(int dueDay) throws Exception {
        return idOf(as(userA, post("/api/years/" + YEAR + "/cards").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nubank\",\"dueDay\":%d}".formatted(dueDay))));
    }

    /** One purchase entered once with automatic installments (REQ-018). */
    private ResultActions purchaseInInstallments(YearMonth from, long cents, int count, boolean amountIsTotal) throws Exception {
        return expense(from, "Eletrônicos", cents, "CARD", 5,
                ",\"generateInstallments\":true,\"installmentCount\":%d,\"amountIsTotal\":%s".formatted(count, amountIsTotal));
    }

    private void autoInstallments(boolean on) throws Exception {
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":%s}".formatted(YEAR, on))).andExpect(status().isOk());
    }

    /** Opens the month (load on first open) and returns the JSON of GET /api/months/{month}. */
    private String open(YearMonth month) throws Exception {
        return as(userA, get("/api/months/" + month)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    private static long cents(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private static List<Long> amounts(String body) {
        List<Number> values = JsonPath.read(body, "$.expenses[*].amountCents");
        return values.stream().map(Number::longValue).toList();
    }

    // ---- Funcionalidade: Totais do mês ----

    @Test
    @DisplayName("Total de gastos soma fixos e cartão")
    void totalExpensesAddsFixedAndCard() throws Exception {
        fixed(APRIL, 60_000, 5);
        fixed(APRIL, 39_399, 10);           // fixos somam 99399
        card(APRIL, 400_000, 8);
        card(APRIL, 94_889, 20);            // cartão soma 494889

        String month = open(APRIL);

        assertThat(cents(month, "$.totals.totalFixedCents")).isEqualTo(99_399);
        assertThat(cents(month, "$.totals.totalExpensesCents")).isEqualTo(594_288);
    }

    @Test
    @DisplayName("Saldo é entradas menos gastos")
    void balanceIsIncomeMinusExpenses() throws Exception {
        income(APRIL, 500_000, 1);
        income(APRIL, 162_358, 20);         // entradas somam 662358
        fixed(APRIL, 99_399, 5);
        card(APRIL, 494_889, 8);            // total de gastos 594288

        String month = open(APRIL);

        assertThat(cents(month, "$.totals.totalIncomeCents")).isEqualTo(662_358);
        assertThat(cents(month, "$.totals.totalExpensesCents")).isEqualTo(594_288);
        assertThat(cents(month, "$.totals.balanceCents")).isEqualTo(68_070);
    }

    // ---- Funcionalidade: Resumo por categoria ----

    @Test
    @DisplayName("Porcentagem sobre o total de gastos")
    void percentageOverTheTotalExpenses() throws Exception {
        idOf(expense(APRIL, "Despesas eventuais", 100_000, "CARD", 3, ""));
        idOf(expense(APRIL, "Despesas eventuais", 41_814, "FIXED", 9, ""));   // "Despesas eventuais" gastou 141814
        card(APRIL, 452_474, 12);                                               // total de gastos 594288

        String month = open(APRIL);

        assertThat(cents(month, "$.totals.totalExpensesCents")).isEqualTo(594_288);
        List<Map<String, Object>> entry = JsonPath.read(month, "$.summaries.byCategory[?(@.categoryName == 'Despesas eventuais')]");
        assertThat(entry).hasSize(1);
        assertThat(((Number) entry.get(0).get("amountCents")).longValue()).isEqualTo(141_814);
        assertThat(new BigDecimal(entry.get(0).get("percent").toString())).isEqualByComparingTo("23.86");
    }

    @Test
    @DisplayName("Categorias fecham com o total")
    void categoriesAddUpToTheTotal() throws Exception {
        String[] categories = {"Mercado", "Lazer", "Saúde", "Contas", "Roupa", "Despesas eventuais"};
        for (int i = 0; i < 30; i++) {
            idOf(expense(APRIL, categories[i % categories.length], 1_000 + 37L * i * i, i % 3 == 0 ? "FIXED" : "CARD",
                    1 + i % 28, ""));
        }

        String month = open(APRIL);

        List<Number> byCategory = JsonPath.read(month, "$.summaries.byCategory[*].amountCents");
        assertThat(byCategory.stream().mapToLong(Number::longValue).sum())
                .isEqualTo(cents(month, "$.totals.totalExpensesCents"));
    }

    // ---- Funcionalidade: Parcelas ----

    @Test
    @DisplayName("Compra dividida sem perder centavos")
    void aPurchaseIsSplitWithoutLosingCents() throws Exception {
        purchaseInInstallments(APRIL, 10_000, 3, true).andExpect(status().isCreated());   // R$ 100,00 em 3 parcelas

        List<Long> installments = List.of(amounts(open(APRIL)).get(0), amounts(open(MAY)).get(0), amounts(open(JUNE)).get(0));

        assertThat(installments).containsExactly(3_333L, 3_333L, 3_334L);
        assertThat(installments.stream().mapToLong(Long::longValue).sum()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("Indicador de parcela")
    void installmentIndicator() throws Exception {
        idOf(expense(APRIL, "Eletrônicos", 2_500, "CARD", 15, ",\"installmentNumber\":7,\"installmentCount\":12"));

        String month = open(APRIL);

        assertThat((String) JsonPath.read(month, "$.expenses[0].installmentLabel")).isEqualTo("7/12");
    }

    // ---- Funcionalidade: Resumo por cartão ----

    @Test
    @DisplayName("Lançamento sem cartão não some do resumo")
    void anExpenseWithoutACardDoesNotVanishFromTheSummary() throws Exception {
        // Adapted (REQ-015/016/037, user decision 2026-11-09): credit without a card is refused and there is no
        // "Sem cartão" group. An old row saved before the rule still counts in the totals until the user fixes it.
        long nubank = createCard(10);
        expense(APRIL, "Mercado", 47_500, "CARD", 9, ",\"cardId\":null")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CARD_REQUIRED"));
        idOf(expense(APRIL, "Mercado", 30_000, "CARD", 9, ",\"cardId\":" + nubank));
        idOf(expense(APRIL, "Assinaturas", 5_590, "FIXED", 9, ",\"paymentMethod\":\"CREDIT\",\"cardId\":" + nubank));
        fixed(APRIL, 99_399, 5);                                                 // Pix: fora do resumo por cartão
        jdbc.update("""
                INSERT INTO expense (user_id, month_ref, name, category_id, amount, type, payment_method, expense_date)
                VALUES (?, ?, 'Antigo', ?, 475.00, 'CARD', 'CREDIT', ?)""",
                userA.id(), APRIL.toString(), categoryId("Mercado"), APRIL.atDay(9));

        String month = open(APRIL);

        List<Map<String, Object>> noCard = JsonPath.read(month, "$.summaries.byCard[?(@.cardId == null)]");
        assertThat(noCard).isEmpty();
        List<Number> byCard = JsonPath.read(month, "$.summaries.byCard[*].amountCents");
        assertThat(byCard.stream().mapToLong(Number::longValue).sum()).isEqualTo(30_000 + 5_590);
        assertThat(cents(month, "$.summaries.byPayment.creditCents")).isEqualTo(47_500 + 30_000 + 5_590);
        assertThat(cents(month, "$.totals.totalExpensesCents")).isEqualTo(47_500 + 30_000 + 5_590 + 99_399);
    }

    // ---- Funcionalidade: Posição por quinzena (adaptada: dias de fechamento, REQ-048) ----

    @Test
    @DisplayName("Falta ou sobra até o dia 15")
    void whatIsLeftOrMissingUntilThe15th() throws Exception {
        income(APRIL, 300_000, 5);
        fixed(APRIL, 120_000, 10);

        String month = open(APRIL);

        assertThat((Integer) JsonPath.read(month, "$.positions[0].toDay")).isEqualTo(15);
        // Adapted (REQ-030): only the expenses of 1–15 count, not the income
        assertThat(cents(month, "$.positions[0].totalExpensesCents")).isEqualTo(120_000);
    }

    @Test
    @DisplayName("Posição até o fim do mês")
    void positionUntilTheEndOfTheMonth() throws Exception {
        income(APRIL, 300_000, 5);
        income(APRIL, 50_000, 25);          // entradas de 350000 até o fim do mês
        fixed(APRIL, 200_000, 8);
        fixed(APRIL, 180_000, 30);          // gastos de 380000 até o fim do mês

        String month = open(APRIL);

        // Adapted (REQ-030): the 2nd interval, 16–30 (31 is the last day of April, REQ-031), sums only its own
        // expenses: 180000. The PDF's running total (−30000) is the month balance.
        assertThat((Integer) JsonPath.read(month, "$.positions[1].fromDay")).isEqualTo(16);
        assertThat((Integer) JsonPath.read(month, "$.positions[1].toDay")).isEqualTo(30);
        assertThat(cents(month, "$.positions[1].totalExpensesCents")).isEqualTo(180_000);
        assertThat(cents(month, "$.totals.balanceCents")).isEqualTo(-30_000);
    }

    @Test
    @DisplayName("Marcar como pago não altera totais")
    void markingAsPaidDoesNotChangeTotals() throws Exception {
        long bill = fixed(APRIL, 43_500, 10);   // um fixo de 43500 centavos não pago
        income(APRIL, 100_000, 1);
        String before = open(APRIL);

        as(userA, patch("/api/expenses/" + bill + "/paid").contentType(MediaType.APPLICATION_JSON).content("{\"paid\":true}"))
                .andExpect(status().isOk());
        String after = open(APRIL);

        assertThat((Boolean) JsonPath.read(after, "$.expenses[0].paid")).isTrue();
        assertThat((Object) JsonPath.read(after, "$.totals")).isEqualTo(JsonPath.read(before, "$.totals"));
        assertThat((Object) JsonPath.read(after, "$.positions")).isEqualTo(JsonPath.read(before, "$.positions"));
        assertThat((Object) JsonPath.read(after, "$.summaries")).isEqualTo(JsonPath.read(before, "$.summaries"));
    }

    // ---- Funcionalidade: Pix e crédito na posição por quinzena ----

    @Test
    @DisplayName("Gasto no Pix pesa na data do pagamento")
    void aPixExpenseWeighsOnThePaymentDate() throws Exception {
        fixed(APRIL, 20_000, 12);           // Pix pago em 12/04

        String month = open(APRIL);

        assertThat((Integer) JsonPath.read(month, "$.positions[0].toDay")).isEqualTo(15);
        assertThat(cents(month, "$.positions[0].totalExpensesCents")).isEqualTo(20_000);
    }

    @Test
    @DisplayName("Gasto no crédito pesa no vencimento do cartão")
    void aCreditExpenseWeighsOnTheCardDueDay() throws Exception {
        long dueOnThe10th = createCard(10);
        idOf(expense(APRIL, "Mercado", 50_000, "CARD", 28, ",\"cardId\":" + dueOnThe10th));   // comprado em 28/04

        String month = open(APRIL);

        assertThat((Integer) JsonPath.read(month, "$.positions[0].toDay")).isEqualTo(15);
        assertThat(cents(month, "$.positions[0].totalExpensesCents")).isEqualTo(50_000);
    }

    // ---- Funcionalidade: Carga ao abrir um mês ----

    @Test
    @DisplayName("Fixos vêm do mês anterior sem a marca de pago")
    void fixedExpensesComeFromThePreviousMonthUnpaid() throws Exception {
        long bill = fixed(MARCH, 43_500, 10);
        as(userA, patch("/api/expenses/" + bill + "/paid").contentType(MediaType.APPLICATION_JSON).content("{\"paid\":true}"))
                .andExpect(status().isOk());   // marcado como pago em março

        String april = open(APRIL);         // abro abril pela primeira vez

        assertThat(amounts(april)).containsExactly(43_500L);
        assertThat((String) JsonPath.read(april, "$.expenses[0].type")).isEqualTo("FIXED");
        assertThat((Boolean) JsonPath.read(april, "$.expenses[0].paid")).isFalse();
        assertThat((Boolean) JsonPath.read(open(MARCH), "$.expenses[0].paid")).isTrue();
    }

    @Test
    @DisplayName("Parcelas continuam no mês seguinte")
    void installmentsContinueInTheNextMonth() throws Exception {
        idOf(expense(MARCH, "Eletrônicos", 2_500, "CARD", 15, ",\"installmentNumber\":7,\"installmentCount\":12"));

        String april = open(APRIL);

        assertThat((List<?>) JsonPath.read(april, "$.expenses")).hasSize(1);
        assertThat((Integer) JsonPath.read(april, "$.expenses[0].installmentNumber")).isEqualTo(8);
        assertThat((Integer) JsonPath.read(april, "$.expenses[0].installmentCount")).isEqualTo(12);
        assertThat((String) JsonPath.read(april, "$.expenses[0].installmentLabel")).isEqualTo("8/12");
    }

    @Test
    @DisplayName("A última parcela não se repete")
    void theLastInstallmentIsNotRepeated() throws Exception {
        idOf(expense(MARCH, "Eletrônicos", 2_500, "CARD", 15, ",\"installmentNumber\":12,\"installmentCount\":12"));

        String april = open(APRIL);

        assertThat((List<?>) JsonPath.read(april, "$.expenses")).isEmpty();
    }

    // ---- Funcionalidade: Parcelas automáticas ----

    @Test
    @DisplayName("Valor total e número de parcelas")
    void totalValueAndNumberOfInstallments() throws Exception {
        autoInstallments(true);
        purchaseInInstallments(APRIL, 10_000, 3, true).andExpect(status().isCreated());

        assertThat(amounts(open(APRIL))).containsExactly(3_333L);
        assertThat(amounts(open(MAY))).containsExactly(3_333L);
        assertThat(amounts(open(JUNE))).containsExactly(3_334L);
        assertThat(amounts(open(JULY))).isEmpty();
    }

    @Test
    @DisplayName("Valor da parcela e número de parcelas")
    void installmentValueAndNumberOfInstallments() throws Exception {
        autoInstallments(true);
        purchaseInInstallments(APRIL, 5_000, 4, false).andExpect(status().isCreated());   // R$ 50,00 em 4 vezes

        for (YearMonth month : List.of(APRIL, MAY, JUNE, JULY)) {
            assertThat(amounts(open(month))).as(month.toString()).containsExactly(5_000L);
        }
        assertThat(amounts(open(JULY.plusMonths(1)))).isEmpty();
    }

    @Test
    @DisplayName("Somente o valor, sem parcelas")
    void onlyTheValueWithoutInstallments() throws Exception {
        autoInstallments(true);
        card(APRIL, 10_000, 5);

        assertThat(amounts(open(APRIL))).containsExactly(10_000L);
        as(userA, get("/api/months/" + APRIL)).andExpect(jsonPath("$.expenses[0].installmentLabel").doesNotExist());
        assertThat(amounts(open(MAY))).isEmpty();
    }

    @Test
    @DisplayName("Permissão desligada não gera parcelas")
    void permissionOffGeneratesNoInstallments() throws Exception {
        autoInstallments(false);
        purchaseInInstallments(APRIL, 10_000, 3, true).andExpect(status().isCreated());

        assertThat(amounts(open(APRIL))).containsExactly(10_000L);
        assertThat(amounts(open(MAY))).isEmpty();
    }

    @Test
    @DisplayName("Parcelas geradas não se repetem na carga do mês")
    void generatedInstallmentsAreNotRepeatedByTheMonthLoad() throws Exception {
        autoInstallments(true);
        purchaseInInstallments(APRIL, 10_000, 3, true).andExpect(status().isCreated());   // abril, maio e junho

        String may = open(MAY);             // abro maio pela primeira vez

        assertThat(amounts(may)).containsExactly(3_333L);
        assertThat((String) JsonPath.read(may, "$.expenses[0].installmentLabel")).isEqualTo("2/3");
        assertThat(amounts(open(MAY))).containsExactly(3_333L);
    }

    // ---- Funcionalidade: Excluir categoria ----

    @Test
    @DisplayName("Categoria com lançamentos não é excluída direto")
    void aCategoryWithExpensesIsNotDeletedRightAway() throws Exception {
        long lazer = categoryId("Lazer");
        idOf(expense(APRIL, "Lazer", 8_000, "CARD", 4, ""));
        idOf(expense(APRIL, "Lazer", 12_000, "CARD", 18, ""));   // 2 lançamentos em abril

        // the API refuses with the count; the screen then asks whether to change those expenses first (REQ-012,
        // front test "asks what to do when the category has expenses, and keeps it")
        as(userA, delete("/api/categories/" + lazer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.count").value(2));

        assertThat(categoryId("Lazer")).isEqualTo(lazer);
    }

    @Test
    @DisplayName("Categoria sem lançamentos é excluída")
    void aCategoryWithoutExpensesIsDeleted() throws Exception {
        long presentes = categoryId("Presentes");

        as(userA, delete("/api/categories/" + presentes)).andExpect(status().isNoContent());

        String list = as(userA, get("/api/years/" + YEAR + "/categories")).andReturn().getResponse().getContentAsString();
        assertThat((List<?>) JsonPath.read(list, "$[?(@.name == 'Presentes')]")).isEmpty();
    }
}
