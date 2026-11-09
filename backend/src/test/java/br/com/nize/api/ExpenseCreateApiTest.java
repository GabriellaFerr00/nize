package br.com.nize.api;

import static br.com.nize.DbMoney.cents;
import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creating one expense (TASK-023): REQ-015, REQ-016, REQ-017, REQ-019, REQ-021, NFR-001, REQ-004. */
class ExpenseCreateApiTest extends IsolationTestBase {

    private static final YearMonth MONTH = YearMonth.of(Year.now().getValue(), 11);
    private static final String URL = "/api/months/" + MONTH + "/expenses";
    private static final String DATE = MONTH.atDay(5).toString();

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    // ---- helpers ----

    private long categoryId(TestUser user, String name) throws Exception {
        String body = as(user, get("/api/years/" + MONTH.getYear() + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private long createCard(TestUser user, String name, int dueDay) throws Exception {
        String body = as(user, post("/api/years/" + MONTH.getYear() + "/cards").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"dueDay\":%d}".formatted(name, dueDay))).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createPerson(TestUser user, String name) throws Exception {
        String body = as(user, post("/api/years/" + MONTH.getYear() + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name))).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Builds the JSON with only the given extra properties (a map of already-JSON-encoded values). */
    private String json(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        fields.forEach((k, v) -> out.append("\"").append(k).append("\":").append(v).append(","));
        out.setLength(out.length() - 1);
        return out.append("}").toString();
    }

    private Map<String, String> base(long category) {
        return new java.util.LinkedHashMap<>(Map.of(
                "name", "\"Aluguel\"", "categoryId", String.valueOf(category), "amountCents", "150000",
                "type", "\"FIXED\"", "date", "\"" + DATE + "\""));
    }

    private ResultActions create(TestUser user, Map<String, String> fields) throws Exception {
        return as(user, post(URL).contentType(MediaType.APPLICATION_JSON).content(json(fields)));
    }

    private List<Map<String, Object>> rows(TestUser user) {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM expense WHERE user_id = ? ORDER BY id", user.id());
    }

    // ---- fixed expenses (REQ-015) ----

    @Test
    void aFixedPixExpenseIsCreatedWithDefaults() throws Exception {
        long category = categoryId(userA, "Aluguel");
        create(userA, base(category))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.month").value(MONTH.toString()))
                .andExpect(jsonPath("$.name").value("Aluguel"))
                .andExpect(jsonPath("$.amountCents").value(150000))
                .andExpect(jsonPath("$.type").value("FIXED"))
                .andExpect(jsonPath("$.paymentMethod").value("PIX"))
                .andExpect(jsonPath("$.paid").value(false))
                .andExpect(jsonPath("$.date").value(DATE))
                .andExpect(jsonPath("$.installmentLabel").doesNotExist());

        Map<String, Object> row = rows(userA).get(0);
        assertThat(row.get("MONTH_REF")).isEqualTo(MONTH.toString());
        assertThat(cents(row.get("AMOUNT"))).isEqualTo(150000L);
        assertThat(row.get("CARD_ID")).isNull();
    }

    @Test
    void aFixedExpenseMayBePaidOnCreditWithOrWithoutACard() throws Exception {
        long category = categoryId(userA, "Assinaturas");
        long card = createCard(userA, "Nubank", 10);

        Map<String, String> onCard = base(category);
        onCard.put("name", "\"Streaming\"");
        onCard.put("paymentMethod", "\"CREDIT\"");
        onCard.put("cardId", String.valueOf(card));
        create(userA, onCard).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FIXED")).andExpect(jsonPath("$.paymentMethod").value("CREDIT"))
                .andExpect(jsonPath("$.cardId").value(card));

        Map<String, String> noCard = base(category);
        noCard.put("paymentMethod", "\"CREDIT\"");
        create(userA, noCard).andExpect(status().isCreated()).andExpect(jsonPath("$.cardId").doesNotExist());
    }

    @Test
    void paidCanBeSetAtCreation() throws Exception {
        Map<String, String> fields = base(categoryId(userA, "Aluguel"));
        fields.put("paid", "true");
        create(userA, fields).andExpect(status().isCreated()).andExpect(jsonPath("$.paid").value(true));
    }

    // ---- card expenses (REQ-016) and third parties (REQ-017) ----

    @Test
    void aCardExpenseIsCreditByDefault() throws Exception {
        long card = createCard(userA, "Nubank", 10);
        Map<String, String> fields = base(categoryId(userA, "Mercado"));
        fields.put("type", "\"CARD\"");
        fields.put("cardId", String.valueOf(card));

        create(userA, fields).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CARD")).andExpect(jsonPath("$.paymentMethod").value("CREDIT"));
    }

    @Test
    void aCardExpenseCannotBePaidWithPix() throws Exception {
        Map<String, String> fields = base(categoryId(userA, "Mercado"));
        fields.put("type", "\"CARD\"");
        fields.put("paymentMethod", "\"PIX\"");
        create(userA, fields).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPENSE"));
        assertThat(rows(userA)).isEmpty();
    }

    @Test
    void aCardCannotBeUsedWithPix() throws Exception {
        long card = createCard(userA, "Nubank", 10);
        Map<String, String> fields = base(categoryId(userA, "Aluguel"));
        fields.put("cardId", String.valueOf(card)); // FIXED defaults to PIX
        create(userA, fields).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPENSE"));
    }

    @Test
    void aThirdPartyExpenseKeepsThePersonAndTheCard() throws Exception {
        long card = createCard(userA, "Nubank", 10);
        long person = createPerson(userA, "Mainha");
        Map<String, String> fields = base(categoryId(userA, "Roupa"));
        fields.put("type", "\"CARD\"");
        fields.put("cardId", String.valueOf(card));
        fields.put("personId", String.valueOf(person));

        create(userA, fields).andExpect(status().isCreated())
                .andExpect(jsonPath("$.personId").value(person)).andExpect(jsonPath("$.cardId").value(card));
        assertThat(((Number) rows(userA).get(0).get("PERSON_ID")).longValue()).isEqualTo(person);
    }

    @Test
    void aPersonOnlyMakesSenseOnACardExpense() throws Exception {
        long person = createPerson(userA, "Mainha");
        Map<String, String> fields = base(categoryId(userA, "Aluguel"));
        fields.put("personId", String.valueOf(person));
        create(userA, fields).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPENSE"));
    }

    // ---- manual installments (REQ-019, REQ-021) ----

    @Test
    void aManualInstallmentCreatesOnlyThatInstallmentWithItsLabel() throws Exception {
        long card = createCard(userA, "Nubank", 10);
        Map<String, String> fields = base(categoryId(userA, "Eletrônicos"));
        fields.put("type", "\"CARD\"");
        fields.put("cardId", String.valueOf(card));
        fields.put("installmentNumber", "7");
        fields.put("installmentCount", "12");

        create(userA, fields).andExpect(status().isCreated())
                .andExpect(jsonPath("$.installmentNumber").value(7))
                .andExpect(jsonPath("$.installmentCount").value(12))
                .andExpect(jsonPath("$.installmentLabel").value("7/12"));
        assertThat(rows(userA)).hasSize(1);
        // its own group, so the month load can continue it later without duplicating
        assertThat(rows(userA).get(0).get("INSTALLMENT_GROUP_ID")).isNotNull();
    }

    @Test
    void installmentsMustBeConsistentAndOnlyOnCardExpenses() throws Exception {
        Map<String, String> cardBase = base(categoryId(userA, "Eletrônicos"));
        cardBase.put("type", "\"CARD\"");

        for (String[] bad : new String[][]{{"7", null}, {null, "12"}, {"13", "12"}, {"0", "12"}, {"1", "1000"}}) {
            Map<String, String> fields = new java.util.LinkedHashMap<>(cardBase);
            if (bad[0] != null) fields.put("installmentNumber", bad[0]);
            if (bad[1] != null) fields.put("installmentCount", bad[1]);
            create(userA, fields).andExpect(status().isBadRequest());
        }
        Map<String, String> fixed = base(categoryId(userA, "Aluguel"));
        fixed.put("installmentNumber", "1");
        fixed.put("installmentCount", "3");
        create(userA, fixed).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPENSE"));
        assertThat(rows(userA)).isEmpty();
    }

    // ---- validation ----

    @Test
    void theDateMustBeInsideTheMonth() throws Exception {
        long category = categoryId(userA, "Aluguel");
        for (LocalDate outside : List.of(MONTH.atDay(1).minusDays(1), MONTH.atEndOfMonth().plusDays(1))) {
            Map<String, String> fields = base(category);
            fields.put("date", "\"" + outside + "\"");
            create(userA, fields).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_OUT_OF_MONTH"));
        }
        Map<String, String> edge = base(category);
        edge.put("date", "\"" + MONTH.atEndOfMonth() + "\"");
        create(userA, edge).andExpect(status().isCreated());
    }

    @Test
    void theMonthMustBeAValidYearMonth() throws Exception {
        long category = categoryId(userA, "Aluguel");
        for (String bad : new String[]{"2026-13", "2026-00", "26-11", "2026-1", "abcd-ef"}) {
            as(userA, post("/api/months/" + bad + "/expenses").contentType(MediaType.APPLICATION_JSON).content(json(base(category))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MONTH"));
        }
    }

    @Test
    void aMonthOfAYearTheUserDoesNotHaveIs404() throws Exception {
        long category = categoryId(userA, "Aluguel");
        as(userA, post("/api/months/2099-01/expenses").contentType(MediaType.APPLICATION_JSON).content(json(base(category))))
                .andExpect(status().isNotFound());
    }

    @Test
    void amountMustBePositiveWholeCentsAndNameIsRequired() throws Exception {
        long category = categoryId(userA, "Aluguel");
        for (String amount : new String[]{"0", "-1"}) {
            Map<String, String> fields = base(category);
            fields.put("amountCents", amount);
            create(userA, fields).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fields.amountCents").exists());
        }
        Map<String, String> decimal = base(category);
        decimal.put("amountCents", "10.5");
        create(userA, decimal).andExpect(status().isBadRequest());

        Map<String, String> noAmount = base(category);
        noAmount.remove("amountCents");
        create(userA, noAmount).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.amountCents").exists());

        Map<String, String> blank = base(category);
        blank.put("name", "\"  \"");
        create(userA, blank).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists());
        Map<String, String> tooLong = base(category);
        tooLong.put("name", "\"" + "x".repeat(121) + "\"");
        create(userA, tooLong).andExpect(status().isBadRequest());
        assertThat(rows(userA)).isEmpty();
    }

    @Test
    void unknownEnumValuesAndBrokenJsonAreBadRequests() throws Exception {
        Map<String, String> fields = base(categoryId(userA, "Aluguel"));
        fields.put("type", "\"WHATEVER\"");
        create(userA, fields).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        as(userA, post(URL).contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isBadRequest());
    }

    @Test
    void largeAmountsKeepEveryCent() throws Exception {
        Map<String, String> fields = base(categoryId(userA, "Aluguel"));
        // the largest amount DECIMAL(12,2) holds: 9 999 999 999.99 reais (D-2)
        fields.put("amountCents", "999999999999");
        create(userA, fields).andExpect(status().isCreated()).andExpect(jsonPath("$.amountCents").value(999_999_999_999L));
        assertThat(rows(userA).get(0).get("AMOUNT")).isEqualTo(new java.math.BigDecimal("9999999999.99"));
        assertThat(cents(rows(userA).get(0).get("AMOUNT"))).isEqualTo(999_999_999_999L);
    }

    @Test
    void anAmountThatDoesNotFitTheColumnIsRejected() throws Exception {
        Map<String, String> fields = base(categoryId(userA, "Aluguel"));
        fields.put("amountCents", "1000000000000");
        create(userA, fields).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(rows(userA)).isEmpty();
    }

    // ---- references: inactive and foreign ----

    @Test
    void inactiveCategoryCardAndPersonAreNotAccepted() throws Exception {
        long category = categoryId(userA, "Lazer");
        long card = createCard(userA, "Velho", 5);
        long person = createPerson(userA, "Antigo");
        as(userA, put("/api/categories/" + category).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lazer\",\"active\":false}")).andExpect(status().isOk());
        as(userA, put("/api/cards/" + card).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Velho\",\"dueDay\":5,\"active\":false}")).andExpect(status().isOk());
        as(userA, put("/api/people/" + person).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Antigo\",\"active\":false}")).andExpect(status().isOk());

        create(userA, base(category)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INACTIVE_REFERENCE"));

        Map<String, String> withCard = base(categoryId(userA, "Mercado"));
        withCard.put("type", "\"CARD\"");
        withCard.put("cardId", String.valueOf(card));
        create(userA, withCard).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INACTIVE_REFERENCE"));

        Map<String, String> withPerson = base(categoryId(userA, "Mercado"));
        withPerson.put("type", "\"CARD\"");
        withPerson.put("personId", String.valueOf(person));
        create(userA, withPerson).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INACTIVE_REFERENCE"));
        assertThat(rows(userA)).isEmpty();
    }

    @Test
    void anUnknownCategoryCardOrPersonIs404() throws Exception {
        create(userA, base(999_999)).andExpect(status().isNotFound());
        Map<String, String> withCard = base(categoryId(userA, "Aluguel"));
        withCard.put("paymentMethod", "\"CREDIT\"");
        withCard.put("cardId", "999999");
        create(userA, withCard).andExpect(status().isNotFound());
    }

    // ---- isolation (REQ-004, NFR-004) ----

    @Test
    void anotherUsersCategoryCardAndPersonAre404AndNothingIsCreated() throws Exception {
        long categoryOfA = categoryId(userA, "Aluguel");
        long cardOfA = createCard(userA, "Nubank", 10);
        long personOfA = createPerson(userA, "Mainha");

        create(userB, base(categoryOfA)).andExpect(status().isNotFound());

        Map<String, String> withCard = base(categoryId(userB, "Aluguel"));
        withCard.put("paymentMethod", "\"CREDIT\"");
        withCard.put("cardId", String.valueOf(cardOfA));
        create(userB, withCard).andExpect(status().isNotFound());

        Map<String, String> withPerson = base(categoryId(userB, "Aluguel"));
        withPerson.put("type", "\"CARD\"");
        withPerson.put("personId", String.valueOf(personOfA));
        create(userB, withPerson).andExpect(status().isNotFound());

        assertThat(rows(userB)).isEmpty();
        assertThat(rows(userA)).isEmpty();
    }

    @Test
    void theExpenseBelongsToTheCallerNoMatterWhatTheBodySays() throws Exception {
        Map<String, String> fields = base(categoryId(userB, "Aluguel"));
        fields.put("userId", String.valueOf(userA.id()));
        create(userB, fields).andExpect(status().isCreated());

        assertThat(rows(userB)).hasSize(1);
        assertThat(rows(userA)).isEmpty();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    // ---- CategoryUsage is now real (REQ-012) ----

    @Test
    void aCategoryWithExpensesCannotBeDeletedAndTheCountIsReal() throws Exception {
        long category = categoryId(userA, "Aluguel");
        create(userA, base(category)).andExpect(status().isCreated());
        create(userA, base(category)).andExpect(status().isCreated());

        as(userA, delete("/api/categories/" + category))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.count").value(2));

        // an unused category of the same user is still deletable, and B's expenses never count for A
        long unused = categoryId(userA, "Lazer");
        as(userA, delete("/api/categories/" + unused)).andExpect(status().isNoContent());
    }
}
