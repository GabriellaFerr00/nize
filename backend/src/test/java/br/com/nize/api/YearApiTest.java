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
        String r = as(user, json(post("/api/years/" + year + "/cards"), "{\"name\":\"%s\",\"dueDay\":%d}".formatted(name, dueDay)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private long createPerson(TestUser user, int year, String name) throws Exception {
        String r = as(user, json(post("/api/years/" + year + "/people"), "{\"name\":\"%s\"}".formatted(name)))
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
    void creatingAYearCopiesCategoriesCardsAndPeopleFromThePreviousYear() throws Exception {
        createCard(userA, THIS, "Nubank", 10);
        createPerson(userA, THIS, "Mainha");

        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated()).andExpect(jsonPath("$.year").value(NEXT));

        String categories = list(userA, "/api/years/" + NEXT + "/categories");
        assertThat((List<String>) JsonPath.read(categories, "$[*].name"))
                .containsExactlyElementsOf(JsonPath.read(list(userA, "/api/years/" + THIS + "/categories"), "$[*].name"));
        assertThat((List<String>) JsonPath.read(categories, "$[*].description")).doesNotContainNull();
        as(userA, get("/api/years/" + NEXT + "/cards"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Nubank"))
                .andExpect(jsonPath("$[0].dueDay").value(10)).andExpect(jsonPath("$[0].year").value(NEXT));
        as(userA, get("/api/years/" + NEXT + "/people"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Mainha"));

        as(userA, get("/api/years")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].year").value(THIS)).andExpect(jsonPath("$[1].year").value(NEXT));
    }

    @Test
    void copiesAreIndependentEditingTheNewYearDoesNotChangeThePreviousOne() throws Exception {
        long nubank = createCard(userA, THIS, "Nubank", 10);
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated());

        String oldCats = list(userA, "/api/years/" + THIS + "/categories");
        String newCats = list(userA, "/api/years/" + NEXT + "/categories");
        assertThat((List<Integer>) JsonPath.read(newCats, "$[*].id")).doesNotContainAnyElementsOf(JsonPath.read(oldCats, "$[*].id"));

        long newMercado = ((Number) JsonPath.read(newCats, "$[0].id")).longValue();
        as(userA, json(put("/api/categories/" + newMercado), "{\"name\":\"Feira\",\"active\":false}")).andExpect(status().isOk());
        long newCard = ((Number) JsonPath.read(list(userA, "/api/years/" + NEXT + "/cards"), "$[0].id")).longValue();
        as(userA, json(put("/api/cards/" + newCard), "{\"name\":\"Nu\",\"dueDay\":28,\"active\":false}")).andExpect(status().isOk());

        as(userA, get("/api/years/" + THIS + "/categories"))
                .andExpect(jsonPath("$[0].name").value("Mercado")).andExpect(jsonPath("$[0].active").value(true));
        as(userA, get("/api/years/" + THIS + "/cards"))
                .andExpect(jsonPath("$[0].id").value(nubank)).andExpect(jsonPath("$[0].name").value("Nubank"))
                .andExpect(jsonPath("$[0].dueDay").value(10)).andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void deactivatedItemsAreCopiedAsDeactivated() throws Exception {
        long mainha = createPerson(userA, THIS, "Mainha");
        as(userA, json(put("/api/people/" + mainha), "{\"name\":\"Mainha\",\"active\":false}")).andExpect(status().isOk());

        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated());

        as(userA, get("/api/years/" + NEXT + "/people"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void creatingAnExistingYearIsAnError() throws Exception {
        as(userA, post("/api/years/" + THIS))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("YEAR_ALREADY_EXISTS"));
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated());
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isConflict());

        as(userA, get("/api/years/" + NEXT + "/categories")).andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void theSourceIsTheClosestEarlierYear() throws Exception {
        createPerson(userA, THIS, "FromThisYear");
        as(userA, post("/api/years/" + (THIS + 4))).andExpect(status().isCreated());
        createPerson(userA, THIS + 4, "FromLater");

        as(userA, post("/api/years/" + (THIS + 2))).andExpect(status().isCreated());

        as(userA, get("/api/years/" + (THIS + 2) + "/people"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("FromThisYear"));
    }

    @Test
    void anEarlierYearWithNothingBeforeItStartsWithTheFactoryCategories() throws Exception {
        createCard(userA, THIS, "Nubank", 10);

        as(userA, post("/api/years/" + (THIS - 1))).andExpect(status().isCreated());

        as(userA, get("/api/years/" + (THIS - 1) + "/categories"))
                .andExpect(jsonPath("$.length()").value(15)).andExpect(jsonPath("$[0].name").value("Mercado"));
        as(userA, get("/api/years/" + (THIS - 1) + "/cards")).andExpect(jsonPath("$.length()").value(0));
        as(userA, get("/api/years")).andExpect(jsonPath("$[0].year").value(THIS - 1)).andExpect(jsonPath("$[0].current").value(false));
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
    void eachUserOnlySeesAndCopiesTheirOwnData() throws Exception {
        createCard(userA, THIS, "OnlyA", 5);
        as(userA, post("/api/years/" + NEXT)).andExpect(status().isCreated());

        // B has no NEXT year yet and sees none of A's data
        as(userB, get("/api/years")).andExpect(jsonPath("$.length()").value(1));
        as(userB, get("/api/years/" + NEXT + "/categories")).andExpect(status().isNotFound());

        // A's NEXT does not block B from creating its own NEXT, which has no cards
        as(userB, post("/api/years/" + NEXT)).andExpect(status().isCreated());
        as(userB, get("/api/years/" + NEXT + "/cards")).andExpect(jsonPath("$.length()").value(0));
        as(userA, get("/api/years/" + NEXT + "/cards")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/years")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/years/" + NEXT)).andExpect(status().isUnauthorized());
    }
}
