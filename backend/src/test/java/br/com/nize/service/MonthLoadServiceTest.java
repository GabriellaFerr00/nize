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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Month load on first open (TASK-027): REQ-022, REQ-023, D-7. Scenarios "Carga ao abrir um mês" of the PDF. */
class MonthLoadServiceTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String JUNE = YEAR + "-06";
    private static final String JULY = YEAR + "-07";

    @Autowired
    private MonthLoadService loader;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long id(TestUser user, int year, String kind, String name) throws Exception {
        String body = as(user, get("/api/years/" + year + "/" + kind)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private long add(TestUser user, String month, String json) throws Exception {
        String body = as(user, post("/api/months/" + month + "/expenses").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long fixed(TestUser user, String month, String name, long cents, boolean paid) throws Exception {
        return add(user, month, "{\"name\":\"%s\",\"categoryId\":%d,\"amountCents\":%d,\"type\":\"FIXED\",\"paid\":%s,\"date\":\"%s-10\"}"
                .formatted(name, id(user, Integer.parseInt(month.substring(0, 4)), "categories", "Aluguel"), cents, paid, month));
    }

    private long oneOff(TestUser user, String month, String name) throws Exception {
        return add(user, month, "{\"name\":\"%s\",\"categoryId\":%d,\"amountCents\":900,\"type\":\"CARD\",\"date\":\"%s-12\"}"
                .formatted(name, id(user, Integer.parseInt(month.substring(0, 4)), "categories", "Lazer"), month));
    }

    private long manualInstallment(TestUser user, String month, String name, int number, int count) throws Exception {
        return add(user, month, "{\"name\":\"%s\",\"categoryId\":%d,\"amountCents\":2500,\"type\":\"CARD\",\"date\":\"%s-15\",\"installmentNumber\":%d,\"installmentCount\":%d}"
                .formatted(name, id(user, Integer.parseInt(month.substring(0, 4)), "categories", "Eletrônicos"), month, number, count));
    }

    private List<Map<String, Object>> expensesOf(TestUser user, String month) {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM expense WHERE user_id = ? AND month_ref = ? ORDER BY id", user.id(), month);
    }

    private void open(TestUser user, String month) {
        loader.open(user.id(), month);
        entityManager.flush();
    }

    // ---- fixed expenses and one-offs ----

    @Test
    void fixedExpensesAreCopiedWithPaidClearedAndOneOffsAreNot() throws Exception {
        fixed(userA, JUNE, "Aluguel", 150_000, true);
        oneOff(userA, JUNE, "Café");

        open(userA, JULY);

        List<Map<String, Object>> july = expensesOf(userA, JULY);
        assertThat(july).hasSize(1);
        Map<String, Object> copy = july.get(0);
        assertThat(copy.get("NAME")).isEqualTo("Aluguel");
        assertThat(cents(copy.get("AMOUNT"))).isEqualTo(150_000L);
        assertThat(copy.get("PAID")).isEqualTo(false);
        assertThat(copy.get("TYPE")).isEqualTo("FIXED");
        assertThat(copy.get("EXPENSE_DATE").toString()).isEqualTo(JULY + "-10");
        assertThat(expensesOf(userA, JUNE)).hasSize(2); // the previous month is untouched
    }

    @Test
    void editingACarriedExpenseDoesNotChangeThePreviousMonth() throws Exception {
        long original = fixed(userA, JUNE, "Aluguel", 150_000, true);
        open(userA, JULY);
        long copy = ((Number) expensesOf(userA, JULY).get(0).get("ID")).longValue();

        as(userA, put("/api/expenses/" + copy).contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"Aluguel novo\",\"categoryId\":%d,\"amountCents\":160000,\"type\":\"FIXED\",\"paid\":false,\"date\":\"%s-10\"}"
                        .formatted(id(userA, YEAR, "categories", "Aluguel"), JULY))).andExpect(status().isOk());
        as(userA, delete("/api/expenses/" + copy)).andExpect(status().isNoContent());

        entityManager.flush();
        Map<String, Object> previous = jdbc.queryForMap("SELECT * FROM expense WHERE id = ?", original);
        assertThat(previous.get("NAME")).isEqualTo("Aluguel");
        assertThat(cents(previous.get("AMOUNT"))).isEqualTo(150_000L);
    }

    @Test
    void openingTheSameMonthTwiceDoesNotDuplicateAnything() throws Exception {
        fixed(userA, JUNE, "Aluguel", 150_000, false);
        manualInstallment(userA, JUNE, "Sofá", 7, 12);

        open(userA, JULY);
        open(userA, JULY);

        assertThat(expensesOf(userA, JULY)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loaded_month WHERE user_id = ? AND month_ref = ?", Integer.class, userA.id(), JULY)).isEqualTo(1);
    }

    @Test
    void aMonthLoadedOnceIsNotLoadedAgainEvenIfTheUserDeletedWhatWasCarried() throws Exception {
        fixed(userA, JUNE, "Aluguel", 150_000, false);
        open(userA, JULY);
        long copy = ((Number) expensesOf(userA, JULY).get(0).get("ID")).longValue();
        as(userA, delete("/api/expenses/" + copy)).andExpect(status().isNoContent());

        open(userA, JULY);

        assertThat(expensesOf(userA, JULY)).isEmpty();
    }

    // ---- installments ----

    @Test
    void theNextInstallmentIsCreatedAlsoForOnesEnteredByHand() throws Exception {
        manualInstallment(userA, JUNE, "Sofá", 7, 12);

        open(userA, JULY);

        Map<String, Object> next = expensesOf(userA, JULY).get(0);
        assertThat(next.get("NAME")).isEqualTo("Sofá");
        assertThat(next.get("INSTALLMENT_NUMBER")).isEqualTo(8);
        assertThat(next.get("INSTALLMENT_COUNT")).isEqualTo(12);
        assertThat(next.get("INSTALLMENT_GROUP_ID")).isEqualTo(expensesOf(userA, JUNE).get(0).get("INSTALLMENT_GROUP_ID"));
        assertThat(cents(next.get("AMOUNT"))).isEqualTo(2_500L);
        assertThat(next.get("PAID")).isEqualTo(false);
        assertThat(next.get("EXPENSE_DATE").toString()).isEqualTo(JULY + "-15");
    }

    @Test
    void theLastInstallmentDoesNotCreateAnotherOne() throws Exception {
        manualInstallment(userA, JUNE, "Sofá", 12, 12);
        open(userA, JULY);
        assertThat(expensesOf(userA, JULY)).isEmpty();
    }

    @Test
    void installmentsGeneratedUpFrontAreNotCreatedAgain() throws Exception {
        add(userA, YEAR + "-05", "{\"name\":\"TV\",\"categoryId\":%d,\"amountCents\":9000,\"type\":\"CARD\",\"date\":\"%s-20\",\"generateInstallments\":true,\"installmentCount\":4}"
                .formatted(id(userA, YEAR, "categories", "Eletrônicos"), YEAR + "-05"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense WHERE user_id = ?", Integer.class, userA.id())).isEqualTo(4);

        open(userA, JUNE);
        open(userA, JULY);

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense WHERE user_id = ?", Integer.class, userA.id())).isEqualTo(4);
    }

    @Test
    void withTheAutomaticPermissionOffOnlyFixedExpensesAreCarried() throws Exception {
        fixed(userA, JUNE, "Aluguel", 150_000, false);
        manualInstallment(userA, JUNE, "Sofá", 7, 12);
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":false}".formatted(YEAR))).andExpect(status().isOk());

        open(userA, JULY);

        assertThat(expensesOf(userA, JULY)).extracting(r -> r.get("NAME")).containsExactly("Aluguel");
    }

    @Test
    void aThirdPartyInstallmentKeepsThePersonAndTheCard() throws Exception {
        long card = ((Number) JsonPath.read(as(userA, post("/api/years/" + YEAR + "/cards").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nubank\",\"dueDay\":10}")).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long person = ((Number) JsonPath.read(as(userA, post("/api/years/" + YEAR + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mainha\"}")).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        add(userA, JUNE, "{\"name\":\"Roupa\",\"categoryId\":%d,\"amountCents\":3000,\"type\":\"CARD\",\"cardId\":%d,\"personId\":%d,\"date\":\"%s-03\",\"installmentNumber\":1,\"installmentCount\":3}"
                .formatted(id(userA, YEAR, "categories", "Roupa"), card, person, JUNE));

        open(userA, JULY);

        Map<String, Object> next = expensesOf(userA, JULY).get(0);
        assertThat(((Number) next.get("CARD_ID")).longValue()).isEqualTo(card);
        assertThat(((Number) next.get("PERSON_ID")).longValue()).isEqualTo(person);
    }

    // ---- year change and edge cases ----

    @Test
    void fromDecemberToJanuaryTheNewYearIsOpenedAndItsStructureCopied() throws Exception {
        fixed(userA, YEAR + "-12", "Aluguel", 150_000, true);
        manualInstallment(userA, YEAR + "-12", "Sofá", 3, 12);
        as(userA, get("/api/years")).andExpect(status().isOk());

        open(userA, (YEAR + 1) + "-01");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_year WHERE user_id = ? AND year_number = ?", Integer.class, userA.id(), YEAR + 1)).isEqualTo(1);
        List<Map<String, Object>> january = expensesOf(userA, (YEAR + 1) + "-01");
        assertThat(january).extracting(r -> r.get("NAME")).containsExactly("Aluguel", "Sofá");
        assertThat(january.get(1).get("INSTALLMENT_NUMBER")).isEqualTo(4);
        // the copies point to the NEW year's categories, found by name
        Long newAluguel = jdbc.queryForObject("SELECT id FROM category WHERE user_id = ? AND year_number = ? AND name = 'Aluguel'", Long.class, userA.id(), YEAR + 1);
        assertThat(((Number) january.get(0).get("CATEGORY_ID")).longValue()).isEqualTo(newAluguel);
        assertThat(january.get(0).get("EXPENSE_DATE").toString()).isEqualTo((YEAR + 1) + "-01-10");
    }

    @Test
    void anItemWhoseCategoryNoLongerExistsInTheNewYearIsSkipped() throws Exception {
        fixed(userA, YEAR + "-12", "Aluguel", 150_000, false);
        manualInstallment(userA, YEAR + "-12", "Sofá", 3, 12);
        as(userA, post("/api/years/" + (YEAR + 1))).andExpect(status().isCreated());
        as(userA, delete("/api/categories/" + id(userA, YEAR + 1, "categories", "Aluguel"))).andExpect(status().isNoContent());

        open(userA, (YEAR + 1) + "-01");

        assertThat(expensesOf(userA, (YEAR + 1) + "-01")).extracting(r -> r.get("NAME")).containsExactly("Sofá");
    }

    @Test
    void theFirstEverMonthLoadsNothingAndIsStillMarkedAsLoaded() throws Exception {
        open(userA, JULY);
        assertThat(expensesOf(userA, JULY)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loaded_month WHERE user_id = ?", Integer.class, userA.id())).isEqualTo(1);
    }

    @Test
    void invalidMonthsAndYearsTheUserCannotReachAreRejected() {
        assertThatThrownBy(() -> loader.open(userA.id(), "2026-13")).isInstanceOf(InvalidExpenseException.class);
        assertThatThrownBy(() -> loader.open(userA.id(), (YEAR + 5) + "-01")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> loader.open(userA.id(), "1999-01")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- isolation ----

    @Test
    void eachUserLoadsOnlyTheirOwnMonth() throws Exception {
        fixed(userA, JUNE, "Aluguel A", 150_000, false);
        fixed(userB, JUNE, "Aluguel B", 90_000, false);

        open(userA, JULY);

        assertThat(expensesOf(userA, JULY)).extracting(r -> r.get("NAME")).containsExactly("Aluguel A");
        assertThat(expensesOf(userB, JULY)).isEmpty();

        open(userB, JULY);
        assertThat(expensesOf(userB, JULY)).extracting(r -> r.get("NAME")).containsExactly("Aluguel B");
        assertThat(expensesOf(userA, JULY)).hasSize(1);
    }

    // ---- incomes (TASK-033, REQ-025, REQ-026) ----

    private long income(TestUser user, String month, String json) throws Exception {
        String body = as(user, post("/api/months/" + month + "/incomes").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long incomeInstallment(TestUser user, String month, String name, int number, int count, String extra) throws Exception {
        return income(user, month, "{\"name\":\"%s\",\"amountCents\":10000,\"date\":\"%s-20\",\"installmentNumber\":%d,\"installmentCount\":%d%s}"
                .formatted(name, month, number, count, extra));
    }

    private List<Map<String, Object>> incomesOf(TestUser user, String month) {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM income WHERE user_id = ? AND month_ref = ? ORDER BY id", user.id(), month);
    }

    private long personOf(TestUser user, int year, String name) throws Exception {
        return ((Number) JsonPath.read(as(user, post("/api/years/" + year + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void anIncomeInOneOfThreeInstallmentsCreatesTheSecondAndThirdWithoutDuplicating() throws Exception {
        incomeInstallment(userA, JUNE, "Venda do sofá", 1, 3, "");
        String august = YEAR + "-08";
        String september = YEAR + "-09";

        open(userA, JULY);
        open(userA, JULY);
        open(userA, august);
        open(userA, september);

        Map<String, Object> second = incomesOf(userA, JULY).get(0);
        assertThat(incomesOf(userA, JULY)).hasSize(1);
        assertThat(second.get("INSTALLMENT_NUMBER")).isEqualTo(2);
        assertThat(second.get("INSTALLMENT_GROUP_ID")).isEqualTo(incomesOf(userA, JUNE).get(0).get("INSTALLMENT_GROUP_ID"));
        assertThat(cents(second.get("AMOUNT"))).isEqualTo(10_000L);
        assertThat(second.get("INCOME_DATE").toString()).isEqualTo(JULY + "-20");
        assertThat(incomesOf(userA, august)).extracting(r -> r.get("INSTALLMENT_NUMBER")).containsExactly(3);
        assertThat(incomesOf(userA, september)).isEmpty();
    }

    @Test
    void aPlainIncomeIsNotCarriedOver() throws Exception {
        income(userA, JUNE, "{\"name\":\"Salário\",\"amountCents\":500000,\"date\":\"%s-05\"}".formatted(JUNE));

        open(userA, JULY);

        assertThat(incomesOf(userA, JULY)).isEmpty();
    }

    @Test
    void withTheAutomaticPermissionOffIncomeInstallmentsAreNotCreated() throws Exception {
        incomeInstallment(userA, JUNE, "Venda do sofá", 1, 3, "");
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":false}".formatted(YEAR))).andExpect(status().isOk());

        open(userA, JULY);

        assertThat(incomesOf(userA, JULY)).isEmpty();
    }

    @Test
    void aReimbursementInInstallmentsKeepsThePersonAcrossTheYear() throws Exception {
        long mainha = personOf(userA, YEAR, "Mainha");
        incomeInstallment(userA, YEAR + "-12", "Mainha devolve", 1, 2, ",\"personId\":" + mainha);

        open(userA, (YEAR + 1) + "-01");

        Long newMainha = jdbc.queryForObject("SELECT id FROM person WHERE user_id = ? AND year_number = ? AND name = 'Mainha'",
                Long.class, userA.id(), YEAR + 1);
        List<Map<String, Object>> january = incomesOf(userA, (YEAR + 1) + "-01");
        assertThat(january).hasSize(1);
        assertThat(((Number) january.get(0).get("PERSON_ID")).longValue()).isEqualTo(newMainha);
    }

    @Test
    void anIncomeWhosePersonNoLongerExistsInTheNewYearIsSkipped() throws Exception {
        long mainha = personOf(userA, YEAR, "Mainha");
        incomeInstallment(userA, YEAR + "-12", "Mainha devolve", 1, 2, ",\"personId\":" + mainha);
        as(userA, post("/api/years/" + (YEAR + 1))).andExpect(status().isCreated());
        long newMainha = id(userA, YEAR + 1, "people", "Mainha");
        as(userA, put("/api/people/" + newMainha).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mãe\",\"active\":true}")).andExpect(status().isOk());

        open(userA, (YEAR + 1) + "-01");

        assertThat(incomesOf(userA, (YEAR + 1) + "-01")).isEmpty();
    }
}
