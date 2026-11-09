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

import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Incomes (TASK-033): create, installments, reimbursement, edit, delete and isolation. REQ-024..026, REQ-023, REQ-004. */
class IncomeApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final YearMonth MONTH = YearMonth.of(YEAR, 6);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private ResultActions create(TestUser user, String month, String json) throws Exception {
        return as(user, post("/api/months/" + month + "/incomes").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long salary(TestUser user) throws Exception {
        return createdId(create(user, MONTH.toString(), """
                {"name":"Salário","amountCents":500000,"date":"%s"}""".formatted(MONTH.atDay(5))));
    }

    private ResultActions update(TestUser user, long id, String json) throws Exception {
        return as(user, put("/api/incomes/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long person(TestUser user, int year, String name) throws Exception {
        String body = as(user, post("/api/years/" + year + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Map<String, Object> row(long id) {
        entityManager.flush();
        return jdbc.queryForMap("SELECT * FROM income WHERE id = ?", id);
    }

    private int count(TestUser user) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM income WHERE user_id = ?", Integer.class, user.id());
    }

    // ---- create (REQ-024) ----

    @Test
    void anIncomeIsCreatedInTheRequestedMonth() throws Exception {
        create(userA, MONTH.toString(), """
                {"name":"  Salário  ","amountCents":500000,"date":"%s"}""".formatted(MONTH.atDay(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.month").value(MONTH.toString()))
                .andExpect(jsonPath("$.name").value("Salário"))
                .andExpect(jsonPath("$.amountCents").value(500_000))
                .andExpect(jsonPath("$.date").value(MONTH.atDay(5).toString()))
                .andExpect(jsonPath("$.reimbursement").value(false))
                .andExpect(jsonPath("$.personId").doesNotExist())
                .andExpect(jsonPath("$.installmentLabel").doesNotExist());
        assertThat(count(userA)).isEqualTo(1);
    }

    @Test
    void invalidIncomesAreRejected() throws Exception {
        String date = MONTH.atDay(5).toString();
        create(userA, MONTH.toString(), "{\"name\":\"\",\"amountCents\":100,\"date\":\"" + date + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(userA, MONTH.toString(), "{\"name\":\"x\",\"amountCents\":0,\"date\":\"" + date + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(userA, MONTH.toString(), "{\"name\":\"x\",\"amountCents\":10.5,\"date\":\"" + date + "\"}")
                .andExpect(status().isBadRequest());
        create(userA, MONTH.toString(), "{\"name\":\"x\",\"amountCents\":100}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(userA, MONTH.toString(), "{\"name\":\"x\",\"amountCents\":100,\"date\":\"" + MONTH.plusMonths(1).atDay(1) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_OUT_OF_MONTH"));
        create(userA, "2026-13", "{\"name\":\"x\",\"amountCents\":100,\"date\":\"" + date + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MONTH"));
        assertThat(count(userA)).isZero();
    }

    @Test
    void aMonthOfAYearTheUserDoesNotHaveIsNotFound() throws Exception {
        create(userA, (YEAR + 5) + "-01", "{\"name\":\"x\",\"amountCents\":100,\"date\":\"" + (YEAR + 5) + "-01-10\"}")
                .andExpect(status().isNotFound());
    }

    // ---- installments (REQ-025) ----

    @Test
    void anInstallmentIncomeGetsItsOwnGroupAndLabel() throws Exception {
        long id = createdId(create(userA, MONTH.toString(), """
                {"name":"Venda do sofá","amountCents":30000,"date":"%s","installmentNumber":1,"installmentCount":3}"""
                .formatted(MONTH.atDay(10))));

        Map<String, Object> row = row(id);
        assertThat(row.get("INSTALLMENT_GROUP_ID")).isNotNull();
        assertThat(((Number) row.get("INSTALLMENT_NUMBER")).intValue()).isEqualTo(1);
        // editing keeps the installment data
        update(userA, id, """
                {"name":"Venda do sofá","amountCents":30000,"date":"%s"}""".formatted(MONTH.atDay(11)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentLabel").value("1/3"));
    }

    @Test
    void invalidInstallmentsAreRejected() throws Exception {
        String date = MONTH.atDay(5).toString();
        for (String installments : List.of(
                "\"installmentNumber\":1", "\"installmentCount\":3", "\"installmentNumber\":4,\"installmentCount\":3",
                "\"installmentNumber\":0,\"installmentCount\":3", "\"installmentNumber\":1,\"installmentCount\":121")) {
            create(userA, MONTH.toString(), "{\"name\":\"x\",\"amountCents\":100,\"date\":\"" + date + "\"," + installments + "}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INCOME"));
        }
        assertThat(count(userA)).isZero();
    }

    // ---- reimbursement (REQ-026) ----

    @Test
    void anIncomeWithAPersonIsAReimbursement() throws Exception {
        long mainha = person(userA, YEAR, "Mainha");

        create(userA, MONTH.toString(), """
                {"name":"Mainha pagou","amountCents":19000,"date":"%s","personId":%d}""".formatted(MONTH.atDay(20), mainha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personId").value(mainha))
                .andExpect(jsonPath("$.reimbursement").value(true));
    }

    @Test
    void aPersonOfAnotherUserOrYearIsNotFound() throws Exception {
        long ofB = person(userB, YEAR, "Mainha");
        create(userA, MONTH.toString(), """
                {"name":"x","amountCents":100,"date":"%s","personId":%d}""".formatted(MONTH.atDay(5), ofB))
                .andExpect(status().isNotFound());

        as(userA, post("/api/years/" + (YEAR + 1)).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated());
        long nextYear = person(userA, YEAR + 1, "Tia");
        create(userA, MONTH.toString(), """
                {"name":"x","amountCents":100,"date":"%s","personId":%d}""".formatted(MONTH.atDay(5), nextYear))
                .andExpect(status().isNotFound());
    }

    @Test
    void anInactivePersonIsRefusedUnlessTheIncomeAlreadyUsesIt() throws Exception {
        long mainha = person(userA, YEAR, "Mainha");
        long id = createdId(create(userA, MONTH.toString(), """
                {"name":"Mainha pagou","amountCents":19000,"date":"%s","personId":%d}""".formatted(MONTH.atDay(20), mainha)));
        as(userA, put("/api/people/" + mainha).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mainha\",\"active\":false}")).andExpect(status().isOk());

        create(userA, MONTH.toString(), """
                {"name":"x","amountCents":100,"date":"%s","personId":%d}""".formatted(MONTH.atDay(5), mainha))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INACTIVE_REFERENCE"));
        update(userA, id, """
                {"name":"Mainha devolveu","amountCents":20000,"date":"%s","personId":%d}""".formatted(MONTH.atDay(21), mainha))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountCents").value(20_000));
    }

    // ---- edit and delete (REQ-023) ----

    @Test
    void editReplacesTheFieldsButNotTheMonth() throws Exception {
        long id = salary(userA);
        long mainha = person(userA, YEAR, "Mainha");

        update(userA, id, """
                {"name":"Reembolso","amountCents":12345,"date":"%s","personId":%d}""".formatted(MONTH.atDay(28), mainha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(MONTH.toString()))
                .andExpect(jsonPath("$.name").value("Reembolso"))
                .andExpect(jsonPath("$.amountCents").value(12_345))
                .andExpect(jsonPath("$.reimbursement").value(true));
        update(userA, id, """
                {"name":"x","amountCents":100,"date":"%s"}""".formatted(MONTH.plusMonths(1).atDay(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_OUT_OF_MONTH"));
        assertThat(cents(row(id).get("AMOUNT"))).isEqualTo(12_345);
    }

    @Test
    void deleteRemovesTheIncome() throws Exception {
        long id = salary(userA);

        as(userA, delete("/api/incomes/" + id)).andExpect(status().isNoContent());
        as(userA, delete("/api/incomes/" + id)).andExpect(status().isNotFound());
        assertThat(count(userA)).isZero();
    }

    // ---- isolation (REQ-004, NFR-004) ----

    @Test
    void onlyTheOwnerCanEditAnIncome() throws Exception {
        long idOfA = salary(userA);
        long idOfB = salary(userB);

        assertOnlyOwnerCanAccess(user -> put("/api/incomes/" + (user.id() == userA.id() ? idOfA : idOfB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"amountCents\":100,\"date\":\"" + MONTH.atDay(3) + "\"}"));
    }

    @Test
    void editCannotPointToAnotherUsersPerson() throws Exception {
        long id = salary(userA);
        long ofB = person(userB, YEAR, "Mainha");

        update(userA, id, """
                {"name":"x","amountCents":100,"date":"%s","personId":%d}""".formatted(MONTH.atDay(5), ofB))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Map<String, Object> row = row(id);
        assertThat(row.get("PERSON_ID")).isNull();
        assertThat(cents(row.get("AMOUNT"))).isEqualTo(500_000L);
    }

    @Test
    void onlyTheOwnerCanDeleteAnIncome() throws Exception {
        long idOfA = salary(userA);
        long idOfB = salary(userB);

        as(userB, delete("/api/incomes/" + idOfA)).andExpect(status().isNotFound());
        as(userA, delete("/api/incomes/" + idOfB)).andExpect(status().isNotFound());
        assertThat(count(userA)).isEqualTo(1);
        assertThat(count(userB)).isEqualTo(1);
        as(userA, delete("/api/incomes/" + idOfA)).andExpect(status().isNoContent());
        as(userB, delete("/api/incomes/" + idOfB)).andExpect(status().isNoContent());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/months/" + MONTH + "/incomes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserIdInTheBodyIsIgnored() throws Exception {
        long id = createdId(create(userA, MONTH.toString(), """
                {"name":"Salário","amountCents":500000,"date":"%s","userId":%d}""".formatted(MONTH.atDay(5), userB.id())));

        assertThat(((Number) row(id).get("USER_ID")).longValue()).isEqualTo(userA.id());
    }
}
