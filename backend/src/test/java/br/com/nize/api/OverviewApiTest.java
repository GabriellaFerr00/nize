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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/years/{year}/overview (TASK-044): expenses, incomes and balance of the 12 months, computed on every read
 * (REQ-039, NFR-002). Months up to the current one are loaded first, January first (D-19). REQ-004 isolation.
 */
class OverviewApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final int PAST = YEAR - 1;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private void createYear(TestUser user, int year) throws Exception {
        as(user, post("/api/years/" + year)).andExpect(status().isCreated());
    }

    private long categoryId(TestUser user, int year) throws Exception {
        String body = as(user, get("/api/years/" + year + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == 'Mercado')].id");
        return ids.get(0);
    }

    private void expense(TestUser user, YearMonth month, long cents, String type) throws Exception {
        as(user, post("/api/months/" + month + "/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(withCard(user, month, "{\"name\":\"Gasto\",\"categoryId\":%d,\"amountCents\":%d,\"type\":\"%s\",\"date\":\"%s\"}"
                        .formatted(categoryId(user, month.getYear()), cents, type, month.atDay(5)))))
                .andExpect(status().isCreated());
    }

    private void income(TestUser user, YearMonth month, long cents) throws Exception {
        as(user, post("/api/months/" + month + "/incomes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Entrada\",\"amountCents\":%d,\"date\":\"%s\"}".formatted(cents, month.atDay(1))))
                .andExpect(status().isCreated());
    }

    private ResultActions overview(TestUser user, int year) throws Exception {
        return as(user, get("/api/years/" + year + "/overview"));
    }

    private int loadedMonths(TestUser user, int year) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM loaded_month WHERE user_id = ? AND month_ref LIKE ?",
                Integer.class, user.id(), year + "-%");
    }

    @Test
    void returnsTheTwelveMonthsWithExpensesIncomesAndBalance() throws Exception {
        createYear(userA, PAST);
        YearMonth march = YearMonth.of(PAST, 3);
        expense(userA, march, 99_399, "FIXED");
        expense(userA, march, 494_889, "CARD");
        income(userA, march, 662_358);

        overview(userA, PAST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(PAST))
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.months[0].month").value(PAST + "-01"))
                .andExpect(jsonPath("$.months[0].totalExpensesCents").value(0))
                .andExpect(jsonPath("$.months[0].totalIncomeCents").value(0))
                .andExpect(jsonPath("$.months[0].balanceCents").value(0))
                .andExpect(jsonPath("$.months[2].month").value(PAST + "-03"))
                .andExpect(jsonPath("$.months[2].totalExpensesCents").value(594_288))
                .andExpect(jsonPath("$.months[2].totalIncomeCents").value(662_358))
                .andExpect(jsonPath("$.months[2].balanceCents").value(68_070))
                // the fixed expense is carried over, month after month (D-19); the card expense and the income are not
                .andExpect(jsonPath("$.months[3].totalExpensesCents").value(99_399))
                .andExpect(jsonPath("$.months[3].totalIncomeCents").value(0))
                .andExpect(jsonPath("$.months[3].balanceCents").value(-99_399))
                .andExpect(jsonPath("$.months[11].month").value(PAST + "-12"))
                .andExpect(jsonPath("$.months[11].totalExpensesCents").value(99_399));
        assertThat(loadedMonths(userA, PAST)).isEqualTo(12);
    }

    @Test
    void everyMonthMatchesTheMonthScreen() throws Exception {
        createYear(userA, PAST);
        expense(userA, YearMonth.of(PAST, 2), 43_500, "FIXED");
        expense(userA, YearMonth.of(PAST, 6), 12_345, "CARD");
        income(userA, YearMonth.of(PAST, 6), 300_000);
        income(userA, YearMonth.of(PAST, 11), 50_000);

        String body = overview(userA, PAST).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (int i = 0; i < 12; i++) {
            String month = YearMonth.of(PAST, i + 1).toString();
            String screen = as(userA, get("/api/months/" + month)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat((String) JsonPath.read(body, "$.months[" + i + "].month")).isEqualTo(month);
            for (String field : List.of("totalExpensesCents", "totalIncomeCents", "balanceCents")) {
                assertThat((Object) JsonPath.read(body, "$.months[" + i + "]." + field))
                        .as(month + " " + field).isEqualTo(JsonPath.read(screen, "$.totals." + field));
            }
        }
    }

    @Test
    void theCurrentYearIsLoadedUpToTheCurrentMonthOnly() throws Exception {
        YearMonth now = YearMonth.now();
        expense(userA, YearMonth.of(YEAR, 1), 10_000, "FIXED");

        String body = overview(userA, YEAR).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        for (int i = 0; i < 12; i++) {
            long expected = i < now.getMonthValue() ? 10_000 : 0;
            assertThat(((Number) JsonPath.read(body, "$.months[" + i + "].totalExpensesCents")).longValue())
                    .as("month " + (i + 1)).isEqualTo(expected);
        }
        assertThat(loadedMonths(userA, YEAR)).isEqualTo(now.getMonthValue());
    }

    @Test
    void aFutureYearLoadsNothingAndOnlyShowsWhatExists() throws Exception {
        int future = YEAR + 1;
        createYear(userA, future);
        expense(userA, YearMonth.of(future, 1), 7_000, "FIXED");

        overview(userA, future)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.months[0].totalExpensesCents").value(7_000))
                .andExpect(jsonPath("$.months[1].totalExpensesCents").value(0));
        assertThat(loadedMonths(userA, future)).isZero();
    }

    @Test
    void readingTwiceDuplicatesNothing() throws Exception {
        createYear(userA, PAST);
        expense(userA, YearMonth.of(PAST, 1), 10_000, "FIXED");

        String first = overview(userA, PAST).andReturn().getResponse().getContentAsString();
        String second = overview(userA, PAST).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(((Number) JsonPath.read(second, "$.months[11].totalExpensesCents")).longValue()).isEqualTo(10_000);
    }

    @Test
    void aYearTheUserDoesNotHaveIsNotFound() throws Exception {
        overview(userA, YEAR + 5)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(loadedMonths(userA, YEAR + 5)).isZero();
    }

    @Test
    void aMalformedYearIsAValidationError() throws Exception {
        as(userA, get("/api/years/abc/overview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/years/" + YEAR + "/overview")).andExpect(status().isUnauthorized());
    }

    // ---- Isolamento (REQ-004) ----

    @Test
    void onlyTheOwnerCanReadTheOverviewOfHerYear() throws Exception {
        createYear(userA, YEAR - 1);
        createYear(userB, YEAR - 2);

        assertOnlyOwnerCanAccess(user -> get("/api/years/" + (user.id() == userA.id() ? YEAR - 1 : YEAR - 2) + "/overview"));
    }

    @Test
    void anotherUsersMovementsNeverShowUp() throws Exception {
        expense(userB, YearMonth.of(YEAR, 1), 55_555, "CARD");
        income(userB, YearMonth.of(YEAR, 1), 66_666);

        overview(userA, YEAR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].totalExpensesCents").value(0))
                .andExpect(jsonPath("$.months[0].totalIncomeCents").value(0));
        overview(userB, YEAR)
                .andExpect(jsonPath("$.months[0].totalExpensesCents").value(55_555))
                .andExpect(jsonPath("$.months[0].totalIncomeCents").value(66_666));
    }
}
