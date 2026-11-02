package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Automatic installments (TASK-024): REQ-018, REQ-009, REQ-020, scenarios "Parcelas automáticas" of the PDF. */
class ExpenseInstallmentsApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long id(TestUser user, String path, String name) throws Exception {
        String body = as(user, get(path)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private long cardId(TestUser user) throws Exception {
        String body = as(user, post("/api/years/" + YEAR + "/cards").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nubank\",\"dueDay\":10}")).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Map<String, String> purchase(TestUser user, long amount, Integer count, boolean amountIsTotal) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("name", "\"Notebook\"");
        f.put("categoryId", String.valueOf(id(user, "/api/years/" + YEAR + "/categories", "Eletrônicos")));
        f.put("amountCents", String.valueOf(amount));
        f.put("type", "\"CARD\"");
        f.put("generateInstallments", "true");
        f.put("amountIsTotal", String.valueOf(amountIsTotal));
        if (count != null) f.put("installmentCount", String.valueOf(count));
        return f;
    }

    private ResultActions generate(TestUser user, String month, int day, Map<String, String> f) throws Exception {
        f.put("date", "\"" + month + "-" + String.format("%02d", day) + "\"");
        StringBuilder json = new StringBuilder("{");
        f.forEach((k, v) -> json.append("\"").append(k).append("\":").append(v).append(","));
        json.setLength(json.length() - 1);
        return as(user, post("/api/months/" + month + "/expenses").contentType(MediaType.APPLICATION_JSON).content(json + "}"));
    }

    private List<Map<String, Object>> rows(TestUser user) {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM expense WHERE user_id = ? ORDER BY month_ref, id", user.id());
    }

    private static long cents(Map<String, Object> row) {
        return ((Number) row.get("AMOUNT_CENTS")).longValue();
    }

    // ---- the PDF scenarios ----

    @Test
    void oneHundredReaisInThreeInstallmentsBecomes3333_3333_3334OneInEachMonth() throws Exception {
        String march = YEAR + "-03";
        generate(userA, march, 15, purchase(userA, 10_000, 3, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(3333))
                .andExpect(jsonPath("$.installmentLabel").value("1/3"))
                .andExpect(jsonPath("$.createdCount").value(3));

        List<Map<String, Object>> rows = rows(userA);
        assertThat(rows).extracting(r -> r.get("MONTH_REF")).containsExactly(YEAR + "-03", YEAR + "-04", YEAR + "-05");
        assertThat(rows).extracting(ExpenseInstallmentsApiTest::cents).containsExactly(3333L, 3333L, 3334L);
        assertThat(rows.stream().mapToLong(ExpenseInstallmentsApiTest::cents).sum()).isEqualTo(10_000L);
        assertThat(rows).extracting(r -> r.get("INSTALLMENT_NUMBER")).containsExactly(1, 2, 3);
        assertThat(rows).extracting(r -> r.get("INSTALLMENT_COUNT")).containsOnly(3);
        assertThat(rows).extracting(r -> r.get("INSTALLMENT_GROUP_ID")).doesNotContainNull().containsOnly(rows.get(0).get("INSTALLMENT_GROUP_ID"));
        assertThat(rows).extracting(r -> r.get("EXPENSE_DATE").toString())
                .containsExactly(YEAR + "-03-15", YEAR + "-04-15", YEAR + "-05-15");
        assertThat(rows).extracting(r -> r.get("TYPE")).containsOnly("CARD");
    }

    @Test
    void fourInstallmentsOfFiftyReaisAreFourTimesFiveThousand() throws Exception {
        generate(userA, YEAR + "-02", 10, purchase(userA, 5_000, 4, false)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCount").value(4));

        assertThat(rows(userA)).extracting(ExpenseInstallmentsApiTest::cents).containsExactly(5_000L, 5_000L, 5_000L, 5_000L);
    }

    @Test
    void withoutInstallmentsItIsASingleExpense() throws Exception {
        for (Integer count : new Integer[]{null, 1}) {
            generate(userA, YEAR + "-02", 10, purchase(userA, 47_500, count, true)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.amountCents").value(47_500)).andExpect(jsonPath("$.createdCount").doesNotExist())
                    .andExpect(jsonPath("$.installmentLabel").doesNotExist());
        }
        List<Map<String, Object>> rows = rows(userA);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("INSTALLMENT_NUMBER")).isNull();
            assertThat(r.get("INSTALLMENT_GROUP_ID")).isNull();
        });
    }

    @Test
    void withTheAutomaticPermissionOffItCreatesASingleExpense() throws Exception {
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":false}".formatted(YEAR))).andExpect(status().isOk());

        generate(userA, YEAR + "-02", 10, purchase(userA, 10_000, 3, true)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(10_000)).andExpect(jsonPath("$.createdCount").doesNotExist());

        List<Map<String, Object>> rows = rows(userA);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("INSTALLMENT_NUMBER")).isNull();
    }

    // ---- details ----

    @Test
    void onlyTheFirstInstallmentCanStartAsPaidAndDatesStayInsideShortMonths() throws Exception {
        Map<String, String> f = purchase(userA, 9_000, 3, true);
        f.put("paid", "true");
        generate(userA, YEAR + "-01", 31, f).andExpect(status().isCreated());

        List<Map<String, Object>> rows = rows(userA);
        assertThat(rows).extracting(r -> r.get("PAID")).containsExactly(true, false, false);
        assertThat(rows).extracting(r -> r.get("EXPENSE_DATE").toString()).containsExactly(
                YEAR + "-01-31", java.time.YearMonth.of(YEAR, 2).atEndOfMonth().toString(), YEAR + "-03-31");
    }

    @Test
    void installmentsCrossingIntoTheNextYearUseThatYearsCatalogAndCreateTheYearIfNeeded() throws Exception {
        long card = cardId(userA);
        Map<String, String> f = purchase(userA, 10_000, 3, true);
        f.put("cardId", String.valueOf(card));
        generate(userA, YEAR + "-11", 5, f).andExpect(status().isCreated());

        List<Map<String, Object>> rows = rows(userA);
        assertThat(rows).extracting(r -> r.get("MONTH_REF")).containsExactly(YEAR + "-11", YEAR + "-12", (YEAR + 1) + "-01");
        as(userA, get("/api/years")).andExpect(jsonPath("$.length()").value(2));

        Long nextYearCategory = jdbc.queryForObject("SELECT id FROM category WHERE user_id = ? AND year_number = ? AND name = 'Eletrônicos'", Long.class, userA.id(), YEAR + 1);
        Long nextYearCard = jdbc.queryForObject("SELECT id FROM card WHERE user_id = ? AND year_number = ? AND name = 'Nubank'", Long.class, userA.id(), YEAR + 1);
        assertThat(((Number) rows.get(2).get("CATEGORY_ID")).longValue()).isEqualTo(nextYearCategory);
        assertThat(((Number) rows.get(2).get("CARD_ID")).longValue()).isEqualTo(nextYearCard);
        assertThat(((Number) rows.get(0).get("CATEGORY_ID")).longValue()).isNotEqualTo(nextYearCategory);
    }

    @Test
    void ifANextYearReferenceIsMissingNothingIsCreated() throws Exception {
        as(userA, post("/api/years/" + (YEAR + 1))).andExpect(status().isCreated());
        long missing = id(userA, "/api/years/" + (YEAR + 1) + "/categories", "Eletrônicos");
        as(userA, delete("/api/categories/" + missing)).andExpect(status().isNoContent());

        generate(userA, YEAR + "-11", 5, purchase(userA, 10_000, 3, true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REFERENCE_UNAVAILABLE"));

        assertThat(rows(userA)).isEmpty();
    }

    // ---- validation ----

    @Test
    void onlyCardExpensesCanBeGeneratedAndTheNumbersMustMakeSense() throws Exception {
        Map<String, String> fixed = purchase(userA, 10_000, 3, true);
        fixed.put("type", "\"FIXED\"");
        generate(userA, YEAR + "-02", 10, fixed).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPENSE"));

        generate(userA, YEAR + "-02", 10, purchase(userA, 2, 3, true)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPENSE")); // 2 cents cannot make 3 installments
        generate(userA, YEAR + "-02", 10, purchase(userA, 10_000, 121, true)).andExpect(status().isBadRequest());
        generate(userA, YEAR + "-02", 10, purchase(userA, 10_000, 0, true)).andExpect(status().isBadRequest());

        Map<String, String> withNumber = purchase(userA, 10_000, 3, true);
        withNumber.put("installmentNumber", "2");
        generate(userA, YEAR + "-02", 10, withNumber).andExpect(status().isBadRequest());

        assertThat(rows(userA)).isEmpty();
    }

    @Test
    void generatedInstallmentsBelongToTheCallerOnly() throws Exception {
        Map<String, String> withForeignCategory = purchase(userA, 10_000, 3, true);
        withForeignCategory.put("categoryId", String.valueOf(id(userA, "/api/years/" + YEAR + "/categories", "Eletrônicos")));
        generate(userB, YEAR + "-02", 10, withForeignCategory).andExpect(status().isNotFound());

        generate(userA, YEAR + "-02", 10, purchase(userA, 10_000, 3, true)).andExpect(status().isCreated());
        assertThat(rows(userB)).isEmpty();
        assertThat(rows(userA)).hasSize(3);
    }
}
