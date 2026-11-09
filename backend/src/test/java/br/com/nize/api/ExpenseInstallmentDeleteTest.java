package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import br.com.nize.service.MonthLoadService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting an installment (TASK-066, REQ-050): only that one (default) or that one and the following ones of the same
 * purchase, for expenses and incomes; the month load never brings the deleted ones back.
 */
class ExpenseInstallmentDeleteTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String MARCH = YEAR + "-03";
    private static final String APRIL = YEAR + "-04";
    private static final String MAY = YEAR + "-05";
    private static final String JUNE = YEAR + "-06";

    @Autowired
    private MonthLoadService loader;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long id(TestUser user, String kind, String name) throws Exception {
        String body = as(user, get("/api/years/" + YEAR + "/" + kind)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private long cardId(TestUser user) throws Exception {
        String body = as(user, post("/api/years/" + YEAR + "/cards").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nubank\",\"dueDay\":10}")).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long create(TestUser user, String path, String json) throws Exception {
        String body = as(user, post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Six installments generated at once, March to August; returns the id of the first. */
    private void sixInstallmentsFromMarch(TestUser user) throws Exception {
        create(user, "/api/months/" + MARCH + "/expenses",
                "{\"name\":\"TV\",\"categoryId\":%d,\"amountCents\":60000,\"type\":\"CARD\",\"cardId\":%d,\"date\":\"%s-20\",\"generateInstallments\":true,\"installmentCount\":6}"
                        .formatted(id(user, "categories", "Eletrônicos"), cardId(user), MARCH));
    }

    private long manualInstallment(TestUser user, String month, int number, int count) throws Exception {
        return create(user, "/api/months/" + month + "/expenses",
                "{\"name\":\"Sofá\",\"categoryId\":%d,\"amountCents\":2500,\"type\":\"CARD\",\"cardId\":%d,\"date\":\"%s-15\",\"installmentNumber\":%d,\"installmentCount\":%d}"
                        .formatted(id(user, "categories", "Eletrônicos"), cardId(user), month, number, count));
    }

    private long manualIncome(TestUser user, String month, int number, int count) throws Exception {
        return create(user, "/api/months/" + month + "/incomes",
                "{\"name\":\"Freela\",\"amountCents\":30000,\"date\":\"%s-05\",\"installmentNumber\":%d,\"installmentCount\":%d}"
                        .formatted(month, number, count));
    }

    private long expenseIdIn(TestUser user, String month) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT id FROM expense WHERE user_id = ? AND month_ref = ?", Long.class, user.id(), month);
    }

    private List<Integer> expenseNumbers(TestUser user) {
        entityManager.flush();
        return jdbc.queryForList("SELECT installment_number FROM expense WHERE user_id = ? ORDER BY installment_number",
                Integer.class, user.id());
    }

    private List<String> expenseMonths(TestUser user) {
        entityManager.flush();
        return jdbc.queryForList("SELECT month_ref FROM expense WHERE user_id = ? ORDER BY month_ref", String.class, user.id());
    }

    private List<Integer> incomeNumbers(TestUser user) {
        entityManager.flush();
        return jdbc.queryForList("SELECT installment_number FROM income WHERE user_id = ? ORDER BY installment_number",
                Integer.class, user.id());
    }

    private void open(TestUser user, String month) {
        loader.open(user.id(), month);
        entityManager.flush();
    }

    // ---- generated installments ----

    @Test
    void thisOneAndTheFollowingDeletesFromThatInstallmentOn() throws Exception {
        sixInstallmentsFromMarch(userA);

        as(userA, delete("/api/expenses/" + expenseIdIn(userA, MAY) + "?scope=following")).andExpect(status().isNoContent());

        // 3/6 (May) to 6/6 (August) are gone; 1/6 and 2/6 stay
        assertThat(expenseNumbers(userA)).containsExactly(1, 2);
        assertThat(expenseMonths(userA)).containsExactly(MARCH, APRIL);
    }

    @Test
    void byDefaultOnlyThatInstallmentIsDeleted() throws Exception {
        sixInstallmentsFromMarch(userA);

        as(userA, delete("/api/expenses/" + expenseIdIn(userA, MAY))).andExpect(status().isNoContent());
        assertThat(expenseNumbers(userA)).containsExactly(1, 2, 4, 5, 6);

        as(userA, delete("/api/expenses/" + expenseIdIn(userA, JUNE) + "?scope=this")).andExpect(status().isNoContent());
        assertThat(expenseNumbers(userA)).containsExactly(1, 2, 5, 6);
    }

    @Test
    void anUnknownScopeIsRejectedAndNothingIsDeleted() throws Exception {
        sixInstallmentsFromMarch(userA);

        as(userA, delete("/api/expenses/" + expenseIdIn(userA, MAY) + "?scope=everything"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(expenseNumbers(userA)).hasSize(6);
    }

    @Test
    void anotherUserCannotDeleteMyInstallmentsAndGets404() throws Exception {
        sixInstallmentsFromMarch(userA);

        as(userB, delete("/api/expenses/" + expenseIdIn(userA, MAY) + "?scope=following")).andExpect(status().isNotFound());
        assertThat(expenseNumbers(userA)).hasSize(6);
    }

    @Test
    void aSingleExpenseIgnoresTheScope() throws Exception {
        long single = create(userA, "/api/months/" + APRIL + "/expenses",
                "{\"name\":\"Café\",\"categoryId\":%d,\"amountCents\":900,\"type\":\"FIXED\",\"date\":\"%s-12\"}"
                        .formatted(id(userA, "categories", "Lazer"), APRIL));
        sixInstallmentsFromMarch(userA);

        as(userA, delete("/api/expenses/" + single + "?scope=following")).andExpect(status().isNoContent());
        assertThat(expenseNumbers(userA)).containsExactly(1, 2, 3, 4, 5, 6);
    }

    // ---- installments entered by hand: the month load must respect the choice ----

    @Test
    void afterDeletingTheFollowingOnesTheNextMonthDoesNotBringThemBack() throws Exception {
        long april = manualInstallment(userA, APRIL, 4, 10);

        as(userA, delete("/api/expenses/" + april + "?scope=following")).andExpect(status().isNoContent());
        open(userA, MAY);
        open(userA, JUNE);

        assertThat(expenseNumbers(userA)).isEmpty();
    }

    @Test
    void deletingOnlyThisOneKeepsTheNextInstallmentsComing() throws Exception {
        long april = manualInstallment(userA, APRIL, 4, 10);

        as(userA, delete("/api/expenses/" + april)).andExpect(status().isNoContent());
        // 5/10 is created in May right away (May was not opened yet), and opening May does not duplicate it
        assertThat(expenseNumbers(userA)).containsExactly(5);
        open(userA, MAY);
        open(userA, JUNE);
        assertThat(expenseNumbers(userA)).containsExactly(5, 6);
        assertThat(expenseMonths(userA)).containsExactly(MAY, JUNE);
    }

    @Test
    void deletingOnlyTheLastInstallmentCreatesNothing() throws Exception {
        long april = manualInstallment(userA, APRIL, 10, 10);

        as(userA, delete("/api/expenses/" + april)).andExpect(status().isNoContent());
        assertThat(expenseNumbers(userA)).isEmpty();
    }

    @Test
    void whenTheNextMonthWasAlreadyOpenedNothingIsCreated() throws Exception {
        long april = manualInstallment(userA, APRIL, 4, 10);
        open(userA, MAY);                                   // creates 5/10 in May
        // only 5/10: June (not opened yet) gets 6/10 so the rest keeps coming
        as(userA, delete("/api/expenses/" + expenseIdIn(userA, MAY))).andExpect(status().isNoContent());
        assertThat(expenseNumbers(userA)).containsExactly(4, 6);

        as(userA, delete("/api/expenses/" + april)).andExpect(status().isNoContent());

        // May was opened and its 5/10 was deleted on purpose: it does not come back
        assertThat(expenseNumbers(userA)).containsExactly(6);
    }

    @Test
    void withAutomaticInstallmentsOffNothingIsCreated() throws Exception {
        long april = manualInstallment(userA, APRIL, 4, 10);
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":false}".formatted(YEAR))).andExpect(status().isOk());

        as(userA, delete("/api/expenses/" + april)).andExpect(status().isNoContent());
        assertThat(expenseNumbers(userA)).isEmpty();
    }

    // ---- incomes in installments ----

    @Test
    void incomesFollowTheSameChoice() throws Exception {
        long march = manualIncome(userA, MARCH, 1, 4);
        open(userA, APRIL);                                 // creates 2/4

        entityManager.flush();
        long april = jdbc.queryForObject("SELECT id FROM income WHERE user_id = ? AND month_ref = ?", Long.class, userA.id(), APRIL);
        as(userA, delete("/api/incomes/" + april + "?scope=following")).andExpect(status().isNoContent());
        open(userA, MAY);
        assertThat(incomeNumbers(userA)).containsExactly(1);

        as(userA, delete("/api/incomes/" + march)).andExpect(status().isNoContent());
        // April was already opened (its 2/4 deleted on purpose): nothing comes back
        assertThat(incomeNumbers(userA)).isEmpty();
    }

    @Test
    void deletingOnlyThisIncomeInstallmentKeepsTheNextOnesComing() throws Exception {
        long march = manualIncome(userA, MARCH, 1, 4);

        as(userA, delete("/api/incomes/" + march + "?scope=this")).andExpect(status().isNoContent());
        open(userA, APRIL);
        open(userA, MAY);

        assertThat(incomeNumbers(userA)).containsExactly(2, 3);
    }

    @Test
    void anotherUserCannotDeleteMyIncomeInstallments() throws Exception {
        long march = manualIncome(userA, MARCH, 1, 4);

        as(userB, delete("/api/incomes/" + march + "?scope=following")).andExpect(status().isNotFound());
        assertThat(incomeNumbers(userA)).containsExactly(1);
    }
}
