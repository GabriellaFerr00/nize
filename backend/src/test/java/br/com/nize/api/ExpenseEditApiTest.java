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

/** Edit, delete and "paid" (TASK-025): REQ-023, REQ-015, REQ-033, REQ-004. */
class ExpenseEditApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final YearMonth MONTH = YearMonth.of(YEAR, 6);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long categoryId(TestUser user, String name) throws Exception {
        String body = as(user, get("/api/years/" + YEAR + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private long create(TestUser user, String type, String extra) throws Exception {
        String json = "{\"name\":\"Aluguel\",\"categoryId\":%d,\"amountCents\":150000,\"type\":\"%s\",\"date\":\"%s\"%s}"
                .formatted(categoryId(user, "Aluguel"), type, MONTH.atDay(5), extra);
        String body = as(user, post("/api/months/" + MONTH + "/expenses").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String edit(long category, long amount, String type, String date, boolean paid, String extra) {
        return "{\"name\":\"Novo nome\",\"categoryId\":%d,\"amountCents\":%d,\"type\":\"%s\",\"date\":\"%s\",\"paid\":%s%s}"
                .formatted(category, amount, type, date, paid, extra);
    }

    private ResultActions update(TestUser user, long id, String json) throws Exception {
        return as(user, put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Map<String, Object> row(long id) {
        entityManager.flush();
        return jdbc.queryForMap("SELECT * FROM expense WHERE id = ?", id);
    }

    private int count(TestUser user) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM expense WHERE user_id = ?", Integer.class, user.id());
    }

    // ---- edit ----

    @Test
    void editChangesTheFieldsButNotTheMonth() throws Exception {
        long id = create(userA, "FIXED", "");
        long category = categoryId(userA, "Contas");

        update(userA, id, edit(category, 99_999, "FIXED", MONTH.atDay(20).toString(), true, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Novo nome"))
                .andExpect(jsonPath("$.amountCents").value(99_999))
                .andExpect(jsonPath("$.categoryId").value(category))
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.date").value(MONTH.atDay(20).toString()));

        Map<String, Object> row = row(id);
        assertThat(row.get("MONTH_REF")).isEqualTo(MONTH.toString());
        assertThat(((Number) row.get("AMOUNT_CENTS")).longValue()).isEqualTo(99_999L);
    }

    @Test
    void theDateMustStayInsideTheExpensesMonth() throws Exception {
        long id = create(userA, "FIXED", "");
        update(userA, id, edit(categoryId(userA, "Aluguel"), 100, "FIXED", MONTH.plusMonths(1).atDay(1).toString(), false, ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_OUT_OF_MONTH"));
        assertThat(row(id).get("EXPENSE_DATE").toString()).isEqualTo(MONTH.atDay(5).toString());
    }

    @Test
    void theSameConsistencyRulesApplyOnEdit() throws Exception {
        long id = create(userA, "FIXED", "");
        long category = categoryId(userA, "Aluguel");
        String date = MONTH.atDay(5).toString();

        update(userA, id, edit(category, 100, "CARD", date, false, ",\"paymentMethod\":\"PIX\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPENSE"));
        update(userA, id, edit(category, 0, "FIXED", date, false, "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amountCents").exists());
        update(userA, id, "{\"name\":\"x\",\"categoryId\":%d,\"amountCents\":100,\"type\":\"FIXED\",\"date\":\"%s\"}".formatted(category, date))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.paid").exists());
        // a fixed expense may be moved to credit, e.g. a subscription put on the card
        update(userA, id, edit(category, 100, "FIXED", date, false, ",\"paymentMethod\":\"CREDIT\"")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT"));
    }

    @Test
    void editingAnInstallmentKeepsItsInstallmentData() throws Exception {
        long id = create(userA, "CARD", ",\"installmentNumber\":7,\"installmentCount\":12");

        update(userA, id, edit(categoryId(userA, "Aluguel"), 4_200, "CARD", MONTH.atDay(9).toString(), false, ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.installmentLabel").value("7/12"));
        assertThat(row(id).get("INSTALLMENT_NUMBER")).isEqualTo(7);

        // an expense with installments cannot become a fixed one
        update(userA, id, edit(categoryId(userA, "Aluguel"), 4_200, "FIXED", MONTH.atDay(9).toString(), false, ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPENSE"));
    }

    @Test
    void anInactiveCategoryIsFineWhenUnchangedButNotWhenChosenNow() throws Exception {
        long id = create(userA, "FIXED", "");
        long aluguel = categoryId(userA, "Aluguel");
        long lazer = categoryId(userA, "Lazer");
        for (long c : new long[]{aluguel, lazer}) {
            String name = c == aluguel ? "Aluguel" : "Lazer";
            as(userA, put("/api/categories/" + c).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"active\":false}")).andExpect(status().isOk());
        }
        String date = MONTH.atDay(5).toString();

        update(userA, id, edit(aluguel, 100, "FIXED", date, false, "")).andExpect(status().isOk());
        update(userA, id, edit(lazer, 100, "FIXED", date, false, "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INACTIVE_REFERENCE"));
    }

    // ---- paid (REQ-033) ----

    @Test
    void markingPaidChangesOnlyTheMarkAndCanBeUndone() throws Exception {
        long id = create(userA, "FIXED", "");
        long before = ((Number) row(id).get("AMOUNT_CENTS")).longValue();

        as(userA, patch("/api/expenses/" + id + "/paid").contentType(MediaType.APPLICATION_JSON).content("{\"paid\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(true)).andExpect(jsonPath("$.amountCents").value(150000));
        Map<String, Object> paid = row(id);
        assertThat(paid.get("PAID")).isEqualTo(true);
        assertThat(((Number) paid.get("AMOUNT_CENTS")).longValue()).isEqualTo(before);
        assertThat(paid.get("EXPENSE_DATE").toString()).isEqualTo(MONTH.atDay(5).toString());

        as(userA, patch("/api/expenses/" + id + "/paid").contentType(MediaType.APPLICATION_JSON).content("{\"paid\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(false));
    }

    @Test
    void thePaidBodyIsRequired() throws Exception {
        long id = create(userA, "FIXED", "");
        as(userA, patch("/api/expenses/" + id + "/paid").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.paid").exists());
    }

    // ---- delete ----

    @Test
    void deleteRemovesOnlyThatExpense() throws Exception {
        long keep = create(userA, "FIXED", "");
        long gone = create(userA, "FIXED", "");

        as(userA, delete("/api/expenses/" + gone)).andExpect(status().isNoContent());

        assertThat(count(userA)).isEqualTo(1);
        assertThat(row(keep)).isNotNull();
        as(userA, delete("/api/expenses/" + gone)).andExpect(status().isNotFound());
        as(userA, delete("/api/expenses/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aDeletedExpenseNoLongerBlocksItsCategory() throws Exception {
        long id = create(userA, "FIXED", "");
        long category = categoryId(userA, "Aluguel");
        as(userA, delete("/api/categories/" + category)).andExpect(status().isConflict()).andExpect(jsonPath("$.count").value(1));

        as(userA, delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        as(userA, delete("/api/categories/" + category)).andExpect(status().isNoContent());
    }

    // ---- isolation (REQ-004, NFR-004) ----

    @Test
    void anotherUserGets404OnPutPatchAndDeleteAndNothingChanges() throws Exception {
        long id = create(userA, "FIXED", "");
        long categoryOfB = categoryId(userB, "Aluguel");

        update(userB, id, edit(categoryOfB, 1, "FIXED", MONTH.atDay(5).toString(), true, "")).andExpect(status().isNotFound());
        as(userB, patch("/api/expenses/" + id + "/paid").contentType(MediaType.APPLICATION_JSON).content("{\"paid\":true}"))
                .andExpect(status().isNotFound());
        as(userB, delete("/api/expenses/" + id)).andExpect(status().isNotFound());

        Map<String, Object> row = row(id);
        assertThat(row.get("NAME")).isEqualTo("Aluguel");
        assertThat(row.get("PAID")).isEqualTo(false);
        assertThat(((Number) row.get("AMOUNT_CENTS")).longValue()).isEqualTo(150000L);
    }

    @Test
    void editCannotPointToAnotherUsersCategory() throws Exception {
        long id = create(userA, "FIXED", "");
        long categoryOfB = categoryId(userB, "Aluguel");
        update(userA, id, edit(categoryOfB, 100, "FIXED", MONTH.atDay(5).toString(), false, "")).andExpect(status().isNotFound());
    }

    @Test
    void editCannotPointToAnotherUsersCardOrPerson() throws Exception {
        long id = create(userA, "CARD", "");
        long category = categoryId(userA, "Aluguel");
        String cardOfB = as(userB, post("/api/years/" + YEAR + "/cards").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nubank\",\"dueDay\":10}")).andReturn().getResponse().getContentAsString();
        String personOfB = as(userB, post("/api/years/" + YEAR + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mainha\"}")).andReturn().getResponse().getContentAsString();
        String date = MONTH.atDay(5).toString();

        update(userA, id, edit(category, 100, "CARD", date, false, ",\"cardId\":" + JsonPath.read(cardOfB, "$.id")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        update(userA, id, edit(category, 100, "CARD", date, false, ",\"personId\":" + JsonPath.read(personOfB, "$.id")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Map<String, Object> row = row(id);
        assertThat(row.get("CARD_ID")).isNull();
        assertThat(row.get("PERSON_ID")).isNull();
        assertThat(((Number) row.get("AMOUNT_CENTS")).longValue()).isEqualTo(150000L);
    }

    @Test
    void ownerAccessWorksAndEverythingRequiresAuthentication() throws Exception {
        long id = create(userA, "FIXED", "");
        long idOfB = create(userB, "FIXED", "");
        assertOnlyOwnerCanAccess(user -> patch("/api/expenses/" + (user.id() == userA.id() ? id : idOfB) + "/paid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"paid\":true}"));

        mvc.perform(put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/expenses/" + id + "/paid").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/expenses/" + id)).andExpect(status().isUnauthorized());
    }
}
