package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** New year + list years (TASK-019): REQ-007, REQ-008, REQ-004. */
class YearApiTest extends IsolationTestBase {

    private static final int THIS = Year.now().getValue();
    private static final int NEXT = THIS + 1;

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String content) {
        return request.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private String list(TestUser user, String path) throws Exception {
        return as(user, get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long createCard(TestUser user, int year, String name, int dueDay) throws Exception {
        String r = as(user, json(post("/api/cards"), "{\"name\":\"%s\",\"dueDay\":%d}".formatted(name, dueDay)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private long createPerson(TestUser user, int year, String name) throws Exception {
        String r = as(user, json(post("/api/people"), "{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    // ---- list years ----

    @Test
    void aNewAccountHasOnlyItsCurrentYear() throws Exception {
        as(userA, get("/api/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].year").value(THIS))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    // ---- create ----

    @Test
    void creatingAYearOpensItsMonthsAndCopiesNothing() throws Exception {
        // D-22: categories, cards and people have no year; the same lists serve every month
        createCard(userA, THIS, "Nubank", 10);
        createPerson(userA, THIS, "Mainha");
        String categoriesBefore = list(userA, "/api/categories");

        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated()).andExpect(jsonPath("$.year").value(NEXT));

        as(userA, get("/api/years")).andExpect(jsonPath("$.length()").value(2));
        assertThat(list(userA, "/api/categories")).isEqualTo(categoriesBefore);
        as(userA, get("/api/cards")).andExpect(jsonPath("$.length()").value(1));
        as(userA, get("/api/people")).andExpect(jsonPath("$.length()").value(1));
        as(userA, get("/api/months/" + NEXT + "-01")).andExpect(status().isOk());
    }

    @Test
    void creatingAnExistingYearIsAnError() throws Exception {
        as(userA, post("/api/years/" + THIS))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("YEAR_ALREADY_EXISTS"));
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated());
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isConflict());

        as(userA, get("/api/categories")).andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void creatingAYearDoesNotChangeTheCurrentYear() throws Exception {
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated());
        as(userA, get("/api/settings")).andExpect(jsonPath("$.currentYear").value(THIS));
        // and the new year can then be chosen as the current one (REQ-006)
        as(userA, json(put("/api/settings"), "{\"currentYear\":%d,\"autoInstallments\":true}".formatted(NEXT))).andExpect(status().isOk());
        as(userA, get("/api/years")).andExpect(jsonPath("$[1].current").value(true));
    }

    @Test
    void yearsOutOfRangeAreRejected() throws Exception {
        as(userA, post("/api/years/1999")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_YEAR"));
        as(userA, post("/api/years/2101")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_YEAR"));
    }

    // ---- isolation ----

    @Test
    void eachUserOnlySeesTheirOwnData() throws Exception {
        createCard(userA, THIS, "OnlyA", 5);
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated());

        // B has no NEXT year yet and sees none of A's data
        as(userB, get("/api/years")).andExpect(jsonPath("$.length()").value(1));
        as(userB, get("/api/cards")).andExpect(jsonPath("$.length()").value(0));

        // A's NEXT does not block B from creating its own NEXT, which has no cards
        as(userB, post("/api/years/" + NEXT)).andExpect(status().isCreated());
        as(userB, get("/api/cards")).andExpect(jsonPath("$.length()").value(0));
        as(userA, get("/api/cards")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/years")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/years/" + NEXT)).andExpect(status().isUnauthorized());
    }
}
