package br.com.nize.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Year;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simple load test of GET /api/months/{month} with 500 expenses in the month (TASK-048, NFR-007: p95 &lt; 500 ms).
 * Tagged {@code performance} and left out of the normal suite; run it with
 * {@code ./mvnw test -Dtest=MonthResponseTimeTest -Dtest.excludedGroups=none}. Results go to docs/cobertura-cenarios.md.
 * <p>
 * No test transaction on purpose: each request runs its own service transaction and builds the response after it
 * ends, as in production, and nothing is cached in a shared persistence context. The user is deleted afterwards.
 */
@Tag("performance")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class MonthResponseTimeTest {

    private static final int EXPENSES = 500;
    private static final int INCOMES = 20;
    private static final int WARM_UP = 30;
    private static final int MEASURED = 300;
    private static final long P95_LIMIT_MS = 500;

    private static final int YEAR = Year.now().getValue();
    private static final YearMonth MONTH = YearMonth.of(YEAR, 4);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private final String email = "load-" + UUID.randomUUID() + "@example.com";
    private String auth;

    @AfterEach
    void deleteUser() {
        Long id = jdbc.query("SELECT id FROM app_user WHERE email = ?", rs -> rs.next() ? rs.getLong(1) : null, email);
        if (id == null) {
            return;
        }
        for (String table : new String[] {"expense", "income", "loaded_month", "closing_day", "settings", "category",
                "card", "person", "user_year"}) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", id);
        }
        jdbc.update("DELETE FROM app_user WHERE id = ?", id);
    }

    private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", auth));
    }

    private long created(String path, String json) throws Exception {
        String body = send(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void signUp() throws Exception {
        String password = "S3cret-pass!";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isCreated());
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        auth = "Bearer " + JsonPath.read(login, "$.token");
    }

    /** 500 expenses through the API, mixing every shape the month screen shows (fixed, card, Pix, credit, third party, installments). */
    private void fillTheMonth() throws Exception {
        String categoriesJson = send(get("/api/years/" + YEAR + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> categories = JsonPath.read(categoriesJson, "$[*].id");
        long[] cards = {
                created("/api/years/" + YEAR + "/cards", "{\"name\":\"Nubank\",\"dueDay\":10}"),
                created("/api/years/" + YEAR + "/cards", "{\"name\":\"Inter\",\"dueDay\":25}"),
                created("/api/years/" + YEAR + "/cards", "{\"name\":\"Bradesco\",\"dueDay\":31}")};
        long[] people = {
                created("/api/years/" + YEAR + "/people", "{\"name\":\"Mainha\"}"),
                created("/api/years/" + YEAR + "/people", "{\"name\":\"Irmã\"}")};

        Random random = new Random(7);
        String url = "/api/months/" + MONTH + "/expenses";
        for (int i = 0; i < EXPENSES; i++) {
            long category = categories.get(i % categories.size());
            long cents = 100 + random.nextInt(50_000);
            String date = MONTH.atDay(1 + random.nextInt(30)).toString();
            String extra = switch (i % 5) {
                case 0 -> ",\"type\":\"FIXED\"";                                                       // fixed, Pix
                case 1 -> ",\"type\":\"FIXED\",\"paymentMethod\":\"CREDIT\",\"cardId\":" + cards[i % 3]; // subscription
                case 2 -> ",\"type\":\"CARD\",\"cardId\":" + cards[i % 3];
                case 3 -> ",\"type\":\"CARD\",\"cardId\":" + cards[i % 3] + ",\"personId\":" + people[i % 2];
                default -> ",\"type\":\"CARD\",\"installmentNumber\":%d,\"installmentCount\":12".formatted(1 + i % 12);
            };
            created(url, "{\"name\":\"Gasto %d\",\"categoryId\":%d,\"amountCents\":%d,\"date\":\"%s\"%s}"
                    .formatted(i, category, cents, date, extra));
        }
        for (int i = 0; i < INCOMES; i++) {
            String person = i % 4 == 0 ? ",\"personId\":" + people[i % 2] : "";
            created("/api/months/" + MONTH + "/incomes", "{\"name\":\"Entrada %d\",\"amountCents\":%d,\"date\":\"%s\"%s}"
                    .formatted(i, 10_000 + random.nextInt(500_000), MONTH.atDay(1 + i), person));
        }
    }

    @Test
    void monthWithFiveHundredExpensesAnswersUnder500MsAtP95() throws Exception {
        signUp();
        fillTheMonth();
        String monthUrl = "/api/months/" + MONTH;

        // the first open runs the month load; it and the warm-up are not measured
        send(get(monthUrl)).andExpect(status().isOk()).andExpect(jsonPath("$.expenses", hasSize(EXPENSES)))
                .andExpect(jsonPath("$.incomes", hasSize(INCOMES)));
        for (int i = 0; i < WARM_UP; i++) {
            send(get(monthUrl)).andExpect(status().isOk());
        }

        long[] micros = new long[MEASURED];
        for (int i = 0; i < MEASURED; i++) {
            long start = System.nanoTime();
            String body = send(get(monthUrl)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            micros[i] = (System.nanoTime() - start) / 1_000;
            assertThat(body).isNotEmpty();
        }
        Arrays.sort(micros);
        double p50 = percentile(micros, 50) / 1000.0;
        double p95 = percentile(micros, 95) / 1000.0;
        double max = micros[MEASURED - 1] / 1000.0;
        System.out.printf("NFR-007 GET %s: %d expenses, %d requests, p50 %.1f ms, p95 %.1f ms, max %.1f ms%n",
                monthUrl, EXPENSES, MEASURED, p50, p95, max);

        assertThat(p95).as("p95 in ms").isLessThan(P95_LIMIT_MS);
    }

    /** Nearest-rank percentile of sorted values. */
    private static long percentile(long[] sorted, int percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }
}
