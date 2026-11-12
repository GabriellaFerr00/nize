package br.com.nize.service;

import static br.com.nize.DbMoney.cents;
import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fixed expense or income also reaches the later months that were already opened (TASK-077, REQ-054); unchecking
 * takes it out of them, and deleting can take "this month and the next ones" (REQ-050).
 */
class FixedInOpenedMonthsTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String MAY = YEAR + "-05";
    private static final String JUNE = YEAR + "-06";
    private static final String JULY = YEAR + "-07";
    private static final String AUGUST = YEAR + "-08";
    private static final String SEPTEMBER = YEAR + "-09";

    @Autowired
    private MonthLoadService loader;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private void open(TestUser user, String month) {
        loader.open(user.id(), month);
        entityManager.flush();
    }

    private long category(TestUser user, String name) throws Exception {
        String body = as(user, get("/api/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private String expenseJson(TestUser user, String month, String type, int day, boolean paid) throws Exception {
        String json = "{\"name\":\"Aluguel\",\"categoryId\":%d,\"amountCents\":150000,\"type\":\"%s\",\"paid\":%s,\"date\":\"%s-%02d\"}"
                .formatted(category(user, "Aluguel"), type, paid, month, day);
        return withCard(user, month, json);
    }

    private long createExpense(TestUser user, String month, String type, int day) throws Exception {
        String body = as(user, post("/api/months/" + month + "/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson(user, month, type, day, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void changeExpense(TestUser user, long id, String month, String type) throws Exception {
        as(user, put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson(user, month, type, 10, true))).andExpect(status().isOk());
    }

    private long createIncome(TestUser user, String month, boolean fixed) throws Exception {
        String body = as(user, post("/api/months/" + month + "/incomes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salário\",\"amountCents\":500000,\"date\":\"%s-05\",\"fixed\":%s}".formatted(month, fixed)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void changeIncome(TestUser user, long id, String month, boolean fixed) throws Exception {
        as(user, put("/api/incomes/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salário\",\"amountCents\":500000,\"date\":\"%s-05\",\"fixed\":%s}".formatted(month, fixed)))
                .andExpect(status().isOk());
    }

    private List<Map<String, Object>> rows(String table, TestUser user, String month) {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE user_id = ? AND month_ref = ? ORDER BY id", user.id(), month);
    }

    private long idOf(String table, TestUser user, String month) {
        return ((Number) rows(table, user, month).get(0).get("ID")).longValue();
    }

    // ---- expenses ----

    @Test
    void aFixedExpenseCreatedLaterReachesTheMonthsAlreadyOpenedWithPaidCleared() throws Exception {
        open(userA, JULY);
        open(userA, AUGUST);
        open(userB, JULY);

        createExpense(userA, JUNE, "FIXED", 30);

        for (String month : List.of(JULY, AUGUST)) {
            List<Map<String, Object>> copies = rows("expense", userA, month);
            assertThat(copies).hasSize(1);
            assertThat(copies.get(0).get("TYPE")).isEqualTo("FIXED");
            assertThat(copies.get(0).get("PAID")).isEqualTo(false);
            assertThat(cents(copies.get(0).get("AMOUNT"))).isEqualTo(150_000L);
            assertThat(copies.get(0).get("RECURRENCE_ID")).isEqualTo(rows("expense", userA, JUNE).get(0).get("RECURRENCE_ID"));
        }
        assertThat(rows("expense", userA, JULY).get(0).get("EXPENSE_DATE").toString()).isEqualTo(JULY + "-30");
        assertThat(rows("expense", userA, AUGUST).get(0).get("EXPENSE_DATE").toString()).isEqualTo(AUGUST + "-30");
        assertThat(rows("expense", userA, MAY)).isEmpty();
        assertThat(rows("expense", userB, JULY)).isEmpty(); // another user's months are not touched
        // a month opened afterwards still gets it from the month before, once
        open(userA, SEPTEMBER);
        assertThat(rows("expense", userA, SEPTEMBER)).hasSize(1);
    }

    @Test
    void anExpenseThatBecomesFixedReachesTheOpenedMonthsAndUncheckingTakesItOut() throws Exception {
        long june = createExpense(userA, JUNE, "CARD", 10);
        open(userA, JULY);
        open(userA, AUGUST);
        assertThat(rows("expense", userA, JULY)).isEmpty();

        changeExpense(userA, june, JUNE, "FIXED");
        assertThat(rows("expense", userA, JULY)).hasSize(1);
        assertThat(rows("expense", userA, AUGUST)).hasSize(1);

        changeExpense(userA, june, JUNE, "CARD");
        assertThat(rows("expense", userA, JULY)).isEmpty();
        assertThat(rows("expense", userA, AUGUST)).isEmpty();
        assertThat(rows("expense", userA, JUNE)).hasSize(1);

        // checking again does not duplicate anything
        changeExpense(userA, june, JUNE, "FIXED");
        changeExpense(userA, june, JUNE, "FIXED");
        assertThat(rows("expense", userA, JULY)).hasSize(1);
    }

    @Test
    void uncheckingAFixedCarriedByTheLoadTakesItOutOfTheNextMonthsOnly() throws Exception {
        long june = createExpense(userA, JUNE, "FIXED", 10);
        open(userA, JULY);
        open(userA, AUGUST);
        long july = idOf("expense", userA, JULY);

        changeExpense(userA, july, JULY, "CARD");

        assertThat(rows("expense", userA, JUNE)).extracting(r -> r.get("TYPE")).containsExactly("FIXED");
        assertThat(rows("expense", userA, JULY)).extracting(r -> r.get("TYPE")).containsExactly("CARD");
        assertThat(rows("expense", userA, AUGUST)).isEmpty();
        assertThat(june).isPositive();
    }

    @Test
    void deletingAFixedWithTheNextOnesRemovesTheLaterCopiesAndNoMonthGetsItAgain() throws Exception {
        createExpense(userA, JUNE, "FIXED", 10);
        open(userA, JULY);
        open(userA, AUGUST);

        as(userA, delete("/api/expenses/" + idOf("expense", userA, JULY) + "?scope=following")).andExpect(status().isNoContent());
        open(userA, SEPTEMBER);

        assertThat(rows("expense", userA, JUNE)).hasSize(1);
        assertThat(rows("expense", userA, JULY)).isEmpty();
        assertThat(rows("expense", userA, AUGUST)).isEmpty();
        assertThat(rows("expense", userA, SEPTEMBER)).isEmpty();
    }

    @Test
    void deletingOnlyThisMonthKeepsTheFixedComingToTheNextMonth() throws Exception {
        createExpense(userA, JUNE, "FIXED", 10);
        open(userA, JULY);

        as(userA, delete("/api/expenses/" + idOf("expense", userA, JULY))).andExpect(status().isNoContent());
        assertThat(rows("expense", userA, JULY)).isEmpty();
        open(userA, AUGUST);

        assertThat(rows("expense", userA, AUGUST)).hasSize(1);
        assertThat(rows("expense", userA, JUNE)).hasSize(1);
    }

    @Test
    void aFixedDeletedInItsFirstMonthDoesNotComeBack() throws Exception {
        long june = createExpense(userA, JUNE, "FIXED", 10);

        as(userA, delete("/api/expenses/" + june)).andExpect(status().isNoContent());
        open(userA, JULY);

        assertThat(rows("expense", userA, JULY)).isEmpty();
    }

    @Test
    void onlyTheOwnerCanDeleteTheNextOnes() throws Exception {
        createExpense(userA, JUNE, "FIXED", 10);
        open(userA, JULY);

        as(userB, delete("/api/expenses/" + idOf("expense", userA, JUNE) + "?scope=following")).andExpect(status().isNotFound());

        assertThat(rows("expense", userA, JULY)).hasSize(1);
    }

    // ---- incomes ----

    @Test
    void aFixedIncomeReachesTheOpenedMonthsUncheckingTakesItOutAndDeleteCanTakeTheNextOnes() throws Exception {
        open(userA, JULY);
        open(userA, AUGUST);

        long june = createIncome(userA, JUNE, true);
        assertThat(rows("income", userA, JULY)).extracting(r -> r.get("FIXED")).containsExactly(true);
        assertThat(rows("income", userA, AUGUST)).hasSize(1);

        changeIncome(userA, june, JUNE, false);
        assertThat(rows("income", userA, JULY)).isEmpty();
        assertThat(rows("income", userA, AUGUST)).isEmpty();

        changeIncome(userA, june, JUNE, true);
        assertThat(rows("income", userA, JULY)).hasSize(1);
        as(userA, delete("/api/incomes/" + idOf("income", userA, JULY) + "?scope=following")).andExpect(status().isNoContent());
        assertThat(rows("income", userA, JULY)).isEmpty();
        assertThat(rows("income", userA, AUGUST)).isEmpty();
        assertThat(rows("income", userA, JUNE)).hasSize(1);
    }

    @Test
    void deletingOnlyThisMonthOfAFixedIncomeKeepsItComing() throws Exception {
        createIncome(userA, JUNE, true);
        open(userA, JULY);

        as(userA, delete("/api/incomes/" + idOf("income", userA, JULY))).andExpect(status().isNoContent());
        open(userA, AUGUST);

        assertThat(rows("income", userA, AUGUST)).hasSize(1);
    }
}
