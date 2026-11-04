package br.com.nize.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Year;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No test transaction here on purpose: the response is built after the service transaction ends, as in production.
 * Catches lazy collections that a {@code @Transactional} test would hide (REQ-048). The rows are committed, so the
 * user is deleted afterwards to keep other tests' counts right.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class SettingsOutsideTransactionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final String email = "settings-" + UUID.randomUUID() + "@example.com";

    @AfterEach
    void deleteUser() {
        Long id = jdbc.query("SELECT id FROM app_user WHERE email = ?", rs -> rs.next() ? rs.getLong(1) : null, email);
        if (id == null) {
            return;
        }
        for (String table : new String[] {"closing_day", "settings", "category", "card", "person", "user_year",
                "loaded_month"}) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", id);
        }
        jdbc.update("DELETE FROM app_user WHERE id = ?", id);
    }

    @Test
    void closingDaysAreReturnedWhenNoTransactionIsOpen() throws Exception {
        String password = "S3cret-pass!";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isCreated());
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(login, "$.token");

        mvc.perform(get("/api/settings").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDays", contains(15, 31)));
        mvc.perform(put("/api/settings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentYear\":%d,\"autoInstallments\":true,\"closingDays\":[10,5]}"
                                .formatted(Year.now().getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDays", contains(5, 10)));
    }
}
