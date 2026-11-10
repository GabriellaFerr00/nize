package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/months/{month} (TASK-034): lists, totals, positions on the closing days and own balance, computed on every
 * read from the rules. REQ-022, REQ-027..034, REQ-048, NFR-002, REQ-004. Numbers from the PDF scenarios.
 */
class MonthApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final YearMonth APRIL = YearMonth.of(YEAR, 4);

    @Autowired
    private JdbcTemplate jdbc;

    private long categoryId(TestUser user) throws Exception {
        String body = as(user, get("/api/years/" + YEAR + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == 'Mercado')].id");
        return ids.get(0);
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long expense(TestUser user, long cents, String type, int day, String extra) throws Exception {
        return idOf(as(user, post("/api/months/" + APRIL + "/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(withCard(user, APRIL, "{\"name\":\"Gasto\",\"categoryId\":%d,\"amountCents\":%d,\"type\":\"%s\",\"date\":\"%s\"%s}"
                        .formatted(categoryId(user), cents, type, APRIL.atDay(day), extra)))));
    }

    private long income(TestUser user, long cents, int day, String extra) throws Exception {
        return idOf(as(user, post("/api/months/" + APRIL + "/incomes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Entrada\",\"amountCents\":%d,\"date\":\"%s\"%s}".formatted(cents, APRIL.atDay(day), extra))));
    }

    private long createIn(TestUser user, String kind, String json) throws Exception {
        return idOf(as(user, post("/api/years/" + YEAR + "/" + kind).contentType(MediaType.APPLICATION_JSON).content(json)));
    }

    private ResultActions month(TestUser user, String month) throws Exception {
        return as(user, get("/api/months/" + month));
    }

    // ---- Totais do mês (REQ-027..029) ----

    @Test
    void totalsFollowThePdfScenarios() throws Exception {
        expense(userA, 99_399, "FIXED", 5, "");
        expense(userA, 494_889, "CARD", 8, "");
        income(userA, 662_358, 1, "");

        month(userA, APRIL.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(APRIL.toString()))
                .andExpect(jsonPath("$.totals.totalExpensesCents").value(594_288))
                .andExpect(jsonPath("$.totals.totalFixedCents").value(99_399))
                .andExpect(jsonPath("$.totals.totalIncomeCents").value(662_358))
                .andExpect(jsonPath("$.totals.balanceCents").value(68_070))
                .andExpect(jsonPath("$.expenses", hasSize(2)))
                .andExpect(jsonPath("$.incomes", hasSize(1)));
    }

    @Test
    void anEmptyMonthIsAllZeroWithTheDefaultPositions() throws Exception {
        month(userA, APRIL.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.balanceCents").value(0))
                .andExpect(jsonPath("$.totals.ownBalanceCents").value(0))
                .andExpect(jsonPath("$.closingsEnabled").value(true))
                .andExpect(jsonPath("$.positions", hasSize(2)))
                .andExpect(jsonPath("$.positions[0].fromDay").value(1))
                .andExpect(jsonPath("$.positions[0].toDay").value(15))
                .andExpect(jsonPath("$.positions[1].fromDay").value(16))
                .andExpect(jsonPath("$.positions[1].toDay").value(30))
                .andExpect(jsonPath("$.expenses", hasSize(0)))
                .andExpect(jsonPath("$.incomes", hasSize(0)));
    }

    // ---- Posições nos fechamentos (REQ-030..033, REQ-048) ----

    @Test
    void positionsUseThePixDateAndTheCardDueDay() throws Exception {
        long card = createIn(userA, "cards", "{\"name\":\"Nubank\",\"dueDay\":10}");
        income(userA, 300_000, 5, "");
        expense(userA, 20_000, "FIXED", 12, "");                                // Pix paid on the 12th
        expense(userA, 50_000, "CARD", 28, ",\"cardId\":" + card);              // due on the 10th, bought on the 28th
        income(userA, 50_000, 25, "");
        expense(userA, 360_000, "FIXED", 30, "");

        month(userA, APRIL.toString())
                .andExpect(jsonPath("$.positions[0].toDay").value(15))
                // 1–15: Pix of the 12th + card due on the 10th; incomes do not count (REQ-030)
                .andExpect(jsonPath("$.positions[0].totalExpensesCents").value(70_000))
                // 16–30 only, without the first interval
                .andExpect(jsonPath("$.positions[1].fromDay").value(16))
                .andExpect(jsonPath("$.positions[1].totalExpensesCents").value(360_000));
    }

    @Test
    void markingAsPaidChangesNoTotalNorPosition() throws Exception {
        long fixed = expense(userA, 43_500, "FIXED", 10, "");
        income(userA, 100_000, 1, "");
        String before = month(userA, APRIL.toString()).andReturn().getResponse().getContentAsString();

        as(userA, patch("/api/expenses/" + fixed + "/paid").contentType(MediaType.APPLICATION_JSON).content("{\"paid\":true}"))
                .andExpect(status().isOk());
        String after = month(userA, APRIL.toString()).andReturn().getResponse().getContentAsString();

        assertThat((Object) JsonPath.read(after, "$.totals")).isEqualTo(JsonPath.read(before, "$.totals"));
        assertThat((Object) JsonPath.read(after, "$.positions")).isEqualTo(JsonPath.read(before, "$.positions"));
        assertThat((Boolean) JsonPath.read(after, "$.expenses[0].paid")).isTrue();
    }

    @Test
    void positionsFollowTheChosenClosingDaysAndDisappearWhenTurnedOff() throws Exception {
        income(userA, 100_000, 1, "");
        expense(userA, 10_000, "FIXED", 7, "");
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":true,\"closingDays\":[5,10,20]}".formatted(YEAR)))
                .andExpect(status().isOk());

        month(userA, APRIL.toString())
                .andExpect(jsonPath("$.positions", hasSize(3)))
                .andExpect(jsonPath("$.positions[0].totalExpensesCents").value(0))        // 1–5 (only an income)
                .andExpect(jsonPath("$.positions[1].totalExpensesCents").value(10_000))   // 6–10
                .andExpect(jsonPath("$.positions[2].fromDay").value(11))             // 11–end of the month
                .andExpect(jsonPath("$.positions[2].toDay").value(30))
                .andExpect(jsonPath("$.positions[2].totalExpensesCents").value(0));

        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":true,\"closingsEnabled\":false}".formatted(YEAR)))
                .andExpect(status().isOk());
        month(userA, APRIL.toString())
                .andExpect(jsonPath("$.closingsEnabled").value(false))
                .andExpect(jsonPath("$.positions", hasSize(0)))
                .andExpect(jsonPath("$.totals.balanceCents").value(90_000));
    }

    // ---- Saldo próprio (REQ-034) ----

    @Test
    void ownBalanceLeavesOutThirdPartyExpensesAndReimbursements() throws Exception {
        long card = createIn(userA, "cards", "{\"name\":\"Nubank\",\"dueDay\":10}");
        long mainha = createIn(userA, "people", "{\"name\":\"Mainha\"}");
        income(userA, 89_000, 1, "");
        expense(userA, 20_000, "CARD", 3, ",\"cardId\":%d,\"personId\":%d".formatted(card, mainha));
        income(userA, 19_000, 20, ",\"personId\":" + mainha);
        expense(userA, 38_000, "FIXED", 5, "");

        // balance 89000 + 19000 − 20000 − 38000 = 50000; own balance 50000 + 20000 − 19000 = 51000
        month(userA, APRIL.toString())
                .andExpect(jsonPath("$.totals.balanceCents").value(50_000))
                .andExpect(jsonPath("$.totals.thirdPartyExpensesCents").value(20_000))
                .andExpect(jsonPath("$.totals.reimbursementsCents").value(19_000))
                .andExpect(jsonPath("$.totals.ownBalanceCents").value(51_000));
    }

    // ---- Resumos (REQ-035..038, TASK-038) ----

    private long categoryNamed(TestUser user, String name) throws Exception {
        String body = as(user, get("/api/years/" + YEAR + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private void expenseIn(TestUser user, String category, long cents, String type, String extra) throws Exception {
        idOf(as(user, post("/api/months/" + APRIL + "/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(withCard(user, APRIL, "{\"name\":\"Gasto\",\"categoryId\":%d,\"amountCents\":%d,\"type\":\"%s\",\"date\":\"%s\"%s}"
                        .formatted(categoryNamed(user, category), cents, type, APRIL.atDay(9), extra)))));
    }

    @Test
    void summariesAlwaysAddUpToTheTotals() throws Exception {
        long nubank = createIn(userA, "cards", "{\"name\":\"Nubank\",\"dueDay\":10}");
        long inter = createIn(userA, "cards", "{\"name\":\"Inter\",\"dueDay\":20}");
        long mainha = createIn(userA, "people", "{\"name\":\"Mainha\"}");
        expenseIn(userA, "Aluguel", 99_399, "FIXED", "");
        expenseIn(userA, "Assinaturas", 5_590, "FIXED", ",\"paymentMethod\":\"CREDIT\",\"cardId\":" + nubank);
        expenseIn(userA, "Mercado", 141_814, "CARD", ",\"cardId\":" + nubank);
        expenseIn(userA, "Mercado", 30_000, "CARD", ",\"cardId\":" + inter + ",\"personId\":" + mainha);
        expenseIn(userA, "Lazer", 47_500, "CARD", "");
        expenseIn(userA, "Roupa", 12_000, "CARD", ",\"cardId\":" + nubank + ",\"personId\":" + mainha);

        String body = month(userA, APRIL.toString()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        long total = ((Number) JsonPath.read(body, "$.totals.totalExpensesCents")).longValue();
        assertThat(total).isEqualTo(336_303);
        List<Number> byCategory = JsonPath.read(body, "$.summaries.byCategory[*].amountCents");
        assertThat(byCategory.stream().mapToLong(Number::longValue).sum()).isEqualTo(total);
        assertThat((String) JsonPath.read(body, "$.summaries.byCategory[0].categoryName")).isEqualTo("Mercado");
        assertThat(((Number) JsonPath.read(body, "$.summaries.byCategory[0].amountCents")).longValue()).isEqualTo(171_814);
        assertThat(JsonPath.read(body, "$.summaries.byCategory[0].percent").toString()).isEqualTo("51.09");

        long pix = ((Number) JsonPath.read(body, "$.summaries.byPayment.pixCents")).longValue();
        long credit = ((Number) JsonPath.read(body, "$.summaries.byPayment.creditCents")).longValue();
        assertThat(pix + credit).isEqualTo(total);
        assertThat(credit).isEqualTo(236_904);
        List<Number> byCard = JsonPath.read(body, "$.summaries.byCard[*].amountCents");
        assertThat(byCard.stream().mapToLong(Number::longValue).sum()).isEqualTo(credit);
        List<Object> noCard = JsonPath.read(body, "$.summaries.byCard[?(@.cardId == null)]");
        assertThat(noCard).isEmpty();                                           // no "Sem cartão" (REQ-037)
        assertThat((String) JsonPath.read(body, "$.summaries.byCard[0].cardName")).isEqualTo("Nubank");

        List<Number> byPerson = JsonPath.read(body, "$.summaries.byPerson[*].amountCents");
        assertThat(byPerson.stream().mapToLong(Number::longValue).sum())
                .isEqualTo(((Number) JsonPath.read(body, "$.totals.thirdPartyExpensesCents")).longValue())
                .isEqualTo(42_000);
        assertThat((String) JsonPath.read(body, "$.summaries.byPerson[0].personName")).isEqualTo("Mainha");
        assertThat((List<?>) JsonPath.read(body, "$.summaries.byPerson[0].byCard")).hasSize(2);
    }

    @Test
    void anEmptyMonthHasEmptySummaries() throws Exception {
        month(userA, APRIL.toString())
                .andExpect(jsonPath("$.summaries.byCategory", hasSize(0)))
                .andExpect(jsonPath("$.summaries.byPayment.pixCents").value(0))
                .andExpect(jsonPath("$.summaries.byPayment.creditCents").value(0))
                .andExpect(jsonPath("$.summaries.byCard", hasSize(0)))
                .andExpect(jsonPath("$.summaries.byPerson", hasSize(0)));
    }

    // ---- Month load (REQ-022) and errors ----

    @Test
    void openingAMonthRunsTheLoadOnce() throws Exception {
        expense(userA, 150_000, "FIXED", 10, "");
        String may = APRIL.plusMonths(1).toString();

        month(userA, may).andExpect(status().isOk()).andExpect(jsonPath("$.expenses", hasSize(1)))
                .andExpect(jsonPath("$.totals.totalFixedCents").value(150_000));
        month(userA, may).andExpect(jsonPath("$.expenses", hasSize(1)));
    }

    @Test
    void invalidOrUnreachableMonthsAreRejected() throws Exception {
        month(userA, "2026-13").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MONTH"));
        month(userA, (YEAR + 5) + "-01").andExpect(status().isNotFound());
    }

    // ---- NFR-002 and isolation (REQ-004) ----

    @Test
    void noTableStoresTotalsOrBalances() {
        assertThat(jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE LOWER(table_schema) = 'public'
                  AND (LOWER(column_name) LIKE '%total%' OR LOWER(column_name) LIKE '%balance%'
                       OR LOWER(column_name) LIKE '%saldo%' OR LOWER(column_name) LIKE '%position%')""", String.class))
                .isEmpty();
    }

    @Test
    void eachUserSeesOnlyTheirOwnMonth() throws Exception {
        expense(userA, 1_000, "FIXED", 5, "");
        income(userA, 7_000, 5, "");
        expense(userB, 2_000, "FIXED", 5, "");

        month(userA, APRIL.toString())
                .andExpect(jsonPath("$.expenses", hasSize(1)))
                .andExpect(jsonPath("$.totals.totalExpensesCents").value(1_000));
        month(userB, APRIL.toString())
                .andExpect(jsonPath("$.expenses", hasSize(1)))
                .andExpect(jsonPath("$.incomes", hasSize(0)))
                .andExpect(jsonPath("$.totals.totalExpensesCents").value(2_000))
                .andExpect(jsonPath("$.totals.totalIncomeCents").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/months/" + APRIL)).andExpect(status().isUnauthorized());
    }
}
