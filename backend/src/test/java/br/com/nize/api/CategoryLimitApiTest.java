package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Optional monthly limit per category (TASK-068, REQ-051): stored, copied to a new year, warned in the month. */
@SuppressWarnings("unchecked")
class CategoryLimitApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String LIST = "/api/categories";
    private static final YearMonth APRIL = YearMonth.of(YEAR, 4);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long categoryId(TestUser user, String name) throws Exception {
        String body = as(user, get(LIST)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    private void setLimit(TestUser user, long id, String name, String limit) throws Exception {
        String extra = limit == null ? "" : ",\"monthlyLimitCents\":" + limit;
        as(user, put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"active\":true%s}".formatted(name, extra)))
                .andExpect(status().isOk());
    }

    private void spend(TestUser user, long categoryId, long cents) throws Exception {
        as(user, post("/api/months/" + APRIL + "/expenses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Gasto\",\"categoryId\":%d,\"amountCents\":%d,\"type\":\"FIXED\",\"date\":\"%s\"}"
                        .formatted(categoryId, cents, APRIL.atDay(3))))
                .andExpect(status().isCreated());
    }

    private String month(TestUser user) throws Exception {
        return as(user, get("/api/months/" + APRIL)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void aLimitIsOptionalAndStoredInReais() throws Exception {
        as(userA, post(LIST).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Delivery\",\"monthlyLimitCents\":100000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.monthlyLimitCents").value(100_000));
        as(userA, post(LIST).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sem limite\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.monthlyLimitCents").doesNotExist());

        entityManager.flush();
        BigDecimal stored = jdbc.queryForObject("SELECT limit_amount FROM category WHERE user_id = ? AND name = 'Delivery'",
                BigDecimal.class, userA.id());
        assertThat(stored).isEqualByComparingTo("1000.00");
    }

    @Test
    void editingSetsChangesAndRemovesTheLimit() throws Exception {
        long mercado = categoryId(userA, "Mercado");

        setLimit(userA, mercado, "Mercado", "50000");
        as(userA, get(LIST)).andExpect(jsonPath("$[?(@.name == 'Mercado')].monthlyLimitCents").value(50_000));

        setLimit(userA, mercado, "Mercado", null);   // full replacement: no limit any more
        as(userA, get(LIST)).andExpect(jsonPath("$[?(@.name == 'Mercado')].monthlyLimitCents").isEmpty());
    }

    @Test
    void aLimitMustBePositiveAndFitTheColumn() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        for (String bad : List.of("0", "-1", "1000000000000", "10.5")) {
            as(userA, put("/api/categories/" + mercado).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Mercado\",\"active\":true,\"monthlyLimitCents\":%s}".formatted(bad)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void theMonthWarnsNearAndOverTheLimitWithoutBlocking() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        long lazer = categoryId(userA, "Lazer");
        long aluguel = categoryId(userA, "Aluguel");
        setLimit(userA, mercado, "Mercado", "100000");
        setLimit(userA, lazer, "Lazer", "100000");

        spend(userA, mercado, 80_000);                 // 80% → near
        spend(userA, lazer, 100_001);                  // over, and still saved
        spend(userA, aluguel, 150_000);                // no limit

        String body = month(userA);
        assertThat((List<Integer>) JsonPath.read(body, "$.summaries.byCategory[?(@.categoryName == 'Mercado')].limitCents"))
                .containsExactly(100_000);
        assertThat((List<String>) JsonPath.read(body, "$.summaries.byCategory[?(@.categoryName == 'Mercado')].limitStatus"))
                .containsExactly("NEAR");
        assertThat((List<String>) JsonPath.read(body, "$.summaries.byCategory[?(@.categoryName == 'Lazer')].limitStatus"))
                .containsExactly("OVER");
        assertThat((List<Object>) JsonPath.read(body, "$.summaries.byCategory[?(@.categoryName == 'Aluguel')].limitStatus"))
                .isEmpty();
        assertThat(((Number) JsonPath.read(body, "$.totals.totalExpensesCents")).longValue()).isEqualTo(330_001);
    }

    @Test
    void underTheLimitTheStatusSaysSo() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        setLimit(userA, mercado, "Mercado", "100000");
        spend(userA, mercado, 79_999);

        as(userA, get("/api/months/" + APRIL)).andExpect(jsonPath("$.summaries.byCategory[0].limitStatus").value("UNDER"));
    }

    @Test
    void aNewYearKeepsTheSameCategoryAndLimit() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        setLimit(userA, mercado, "Mercado", "70000");

        as(userA, post("/api/years/" + (YEAR + 1))).andExpect(status().isCreated());

        as(userA, get("/api/categories"))
                .andExpect(jsonPath("$[?(@.name == 'Mercado')].monthlyLimitCents").value(70_000));
    }

    @Test
    void anotherUserCannotSetMyLimit() throws Exception {
        long mercado = categoryId(userA, "Mercado");

        as(userB, put("/api/categories/" + mercado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mercado\",\"active\":true,\"monthlyLimitCents\":1}"))
                .andExpect(status().isNotFound());
        as(userA, get(LIST)).andExpect(jsonPath("$[?(@.name == 'Mercado')].monthlyLimitCents").isEmpty());
    }

    // ---- the month's own limit (REQ-051, D-22, TASK-074) ----

    private org.springframework.test.web.servlet.ResultActions setMonth(TestUser user, YearMonth month, long category, String cents)
            throws Exception {
        return as(user, put("/api/months/" + month + "/category-limits/" + category).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitCents\":%s}".formatted(cents)));
    }

    private String limitsOf(TestUser user, YearMonth month) throws Exception {
        return as(user, get("/api/months/" + month + "/category-limits")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aMonthCanHaveItsOwnLimitAndTheOtherMonthsKeepTheDefault() throws Exception {
        long lazer = categoryId(userA, "Lazer");
        setLimit(userA, lazer, "Lazer", "50000");
        setMonth(userA, APRIL, lazer, "120000").andExpect(status().isNoContent());

        assertThat((List<Integer>) JsonPath.read(limitsOf(userA, APRIL), "$[?(@.categoryName == 'Lazer')].defaultCents")).containsExactly(50_000);
        assertThat((List<Integer>) JsonPath.read(limitsOf(userA, APRIL), "$[?(@.categoryName == 'Lazer')].monthCents")).containsExactly(120_000);
        assertThat((List<Object>) JsonPath.read(limitsOf(userA, APRIL.plusMonths(1)), "$[?(@.categoryName == 'Lazer')].monthCents"))
                .containsExactly((Object) null);

        // the month uses its own limit: 100000 of 120000 is near, not over the default 50000
        spend(userA, lazer, 100_000);
        String body = month(userA);
        assertThat((List<Integer>) JsonPath.read(body, "$.summaries.byCategory[?(@.categoryName == 'Lazer')].limitCents")).containsExactly(120_000);
        assertThat((List<String>) JsonPath.read(body, "$.summaries.byCategory[?(@.categoryName == 'Lazer')].limitStatus")).containsExactly("NEAR");
    }

    @Test
    void changingTheMonthAgainReplacesItAndRemovingBringsTheDefaultBack() throws Exception {
        long lazer = categoryId(userA, "Lazer");
        setLimit(userA, lazer, "Lazer", "50000");
        setMonth(userA, APRIL, lazer, "120000").andExpect(status().isNoContent());
        setMonth(userA, APRIL, lazer, "90000").andExpect(status().isNoContent());
        assertThat((List<Integer>) JsonPath.read(limitsOf(userA, APRIL), "$[?(@.categoryName == 'Lazer')].monthCents")).containsExactly(90_000);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category_month_limit", Integer.class)).isEqualTo(1);

        as(userA, delete("/api/months/" + APRIL + "/category-limits/" + lazer)).andExpect(status().isNoContent());

        spend(userA, lazer, 60_000);
        assertThat((List<String>) JsonPath.read(month(userA), "$.summaries.byCategory[?(@.categoryName == 'Lazer')].limitStatus"))
                .containsExactly("OVER");
    }

    @Test
    void aMonthLimitWorksEvenWithoutADefault() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        setMonth(userA, APRIL, mercado, "10000").andExpect(status().isNoContent());
        spend(userA, mercado, 10_001);

        assertThat((List<String>) JsonPath.read(month(userA), "$.summaries.byCategory[?(@.categoryName == 'Mercado')].limitStatus"))
                .containsExactly("OVER");
    }

    @Test
    void aMonthLimitMustBePositiveAndTheMonthValid() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        for (String bad : List.of("0", "-5", "1000000000000", "null", "1.5")) {
            setMonth(userA, APRIL, mercado, bad).andExpect(status().isBadRequest());
        }
        as(userA, put("/api/months/2026-13/category-limits/" + mercado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitCents\":100}")).andExpect(status().isBadRequest());
    }

    @Test
    void deletingACategoryAlsoDeletesItsMonthLimits() throws Exception {
        String created = as(userA, post(LIST).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pets\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long pets = ((Number) JsonPath.read(created, "$.id")).longValue();
        setMonth(userA, APRIL, pets, "5000").andExpect(status().isNoContent());

        as(userA, delete("/api/categories/" + pets)).andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category_month_limit", Integer.class)).isZero();
    }

    @Test
    void onlyTheOwnerSeesAndChangesTheMonthLimits() throws Exception {
        long mercadoOfA = categoryId(userA, "Mercado");
        long mercadoOfB = categoryId(userB, "Mercado");
        setMonth(userA, APRIL, mercadoOfA, "7000").andExpect(status().isNoContent());

        assertOnlyOwnerCanAccess(user -> put("/api/months/" + APRIL + "/category-limits/" + (user.id() == userA.id() ? mercadoOfA : mercadoOfB))
                .contentType(MediaType.APPLICATION_JSON).content("{\"limitCents\":8000}"));
        as(userB, delete("/api/months/" + APRIL + "/category-limits/" + mercadoOfA)).andExpect(status().isNotFound());
        assertThat((List<Object>) JsonPath.read(limitsOf(userB, APRIL), "$[*].monthCents")).containsOnly((Object) null, 8_000);
        assertThat((List<Integer>) JsonPath.read(limitsOf(userA, APRIL), "$[?(@.categoryName == 'Mercado')].monthCents")).containsExactly(8_000);
    }
}
