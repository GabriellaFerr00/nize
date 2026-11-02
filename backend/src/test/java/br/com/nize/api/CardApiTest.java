package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Card CRUD (TASK-016): REQ-013, REQ-004, NFR-004. */
class CardApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String LIST = "/api/years/" + YEAR + "/cards";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private String create(String name, Object dueDay) {
        return "{\"name\":\"%s\",\"dueDay\":%s}".formatted(name, dueDay);
    }

    private String update(String name, Object dueDay, Object active) {
        return "{\"name\":\"%s\",\"dueDay\":%s,\"active\":%s}".formatted(name, dueDay, active);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String content) {
        return request.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private long createCard(TestUser user, String name, int dueDay) throws Exception {
        String response = as(user, json(post(LIST), create(name, dueDay)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private List<Integer> idsOf(TestUser user, String url) throws Exception {
        String response = as(user, get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$[*].id");
    }

    // ---- list and create ----

    @Test
    void aNewAccountHasNoCardsYet() throws Exception {
        as(userA, get(LIST)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createAddsACardWithItsDueDay() throws Exception {
        as(userA, json(post(LIST), create("  Nubank  ", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.year").value(YEAR))
                .andExpect(jsonPath("$.name").value("Nubank"))
                .andExpect(jsonPath("$.dueDay").value(10))
                .andExpect(jsonPath("$.active").value(true));

        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Nubank"));
        as(userB, get(LIST)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dueDayMustBeBetween1And31() throws Exception {
        for (Object invalid : new Object[]{0, 32, -1, 100, "null"}) {
            as(userA, json(post(LIST), create("Bad" + invalid, invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fields.dueDay").exists());
        }
        as(userA, json(post(LIST), create("First", 1))).andExpect(status().isCreated());
        as(userA, json(post(LIST), create("Last", 31))).andExpect(status().isCreated());
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void nameIsRequiredAndLimitedTo80() throws Exception {
        as(userA, json(post(LIST), create("", 5)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists());
        as(userA, json(post(LIST), create("x".repeat(81), 5))).andExpect(status().isBadRequest());
    }

    @Test
    void aRepeatedNameInTheSameYearIsRejectedIgnoringCase() throws Exception {
        createCard(userA, "Nubank", 10);
        as(userA, json(post(LIST), create("NUBANK", 12)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CARD_NAME_TAKEN"));
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theSameNameIsFineForAnotherUserAndAnotherYear() throws Exception {
        createCard(userA, "Nubank", 10);
        createCard(userB, "Nubank", 10);

        entityManager.flush();
        jdbc.update("INSERT INTO user_year (user_id, year_number) VALUES (?, 2030)", userA.id());
        as(userA, json(post("/api/years/2030/cards"), create("Nubank", 10))).andExpect(status().isCreated());
    }

    @Test
    void aYearTheUserDoesNotHaveIs404() throws Exception {
        as(userA, get("/api/years/2099/cards")).andExpect(status().isNotFound());
        as(userA, json(post("/api/years/2099/cards"), create("Nubank", 10))).andExpect(status().isNotFound());
    }

    // ---- update ----

    @Test
    void renameAndChangeTheDueDay() throws Exception {
        long id = createCard(userA, "Nubank", 10);
        as(userA, json(put("/api/cards/" + id), update("Nubank Roxinho", 15, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Nubank Roxinho"))
                .andExpect(jsonPath("$.dueDay").value(15));

        as(userA, get(LIST)).andExpect(jsonPath("$[0].dueDay").value(15));
    }

    @Test
    void updateValidatesTheDueDayAndRequiresActive() throws Exception {
        long id = createCard(userA, "Nubank", 10);
        as(userA, json(put("/api/cards/" + id), update("Nubank", 0, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.dueDay").exists());
        as(userA, json(put("/api/cards/" + id), update("Nubank", 32, true))).andExpect(status().isBadRequest());
        as(userA, json(put("/api/cards/" + id), "{\"name\":\"Nubank\",\"dueDay\":10}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.active").exists());

        as(userA, get(LIST)).andExpect(jsonPath("$[0].dueDay").value(10));
    }

    @Test
    void renamingToAnExistingNameIsRejectedButKeepingOrRecasingItIsFine() throws Exception {
        long nubank = createCard(userA, "Nubank", 10);
        createCard(userA, "Bradesco", 20);

        as(userA, json(put("/api/cards/" + nubank), update("bradesco", 10, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CARD_NAME_TAKEN"));
        as(userA, json(put("/api/cards/" + nubank), update("Nubank", 11, true))).andExpect(status().isOk());
        as(userA, json(put("/api/cards/" + nubank), update("NUBANK", 11, true))).andExpect(status().isOk());
    }

    @Test
    void deactivatedCardsLeaveTheActiveListButStayInTheFullList() throws Exception {
        long id = createCard(userA, "Nubank", 10);
        createCard(userA, "Bradesco", 20);

        as(userA, json(put("/api/cards/" + id), update("Nubank", 10, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        assertThat(idsOf(userA, LIST + "?active=true")).doesNotContain((int) id).hasSize(1);
        assertThat(idsOf(userA, LIST)).contains((int) id).hasSize(2);

        as(userA, json(put("/api/cards/" + id), update("Nubank", 10, true))).andExpect(status().isOk());
        assertThat(idsOf(userA, LIST + "?active=true")).hasSize(2);
    }

    @Test
    void unknownCardIs404() throws Exception {
        as(userA, json(put("/api/cards/999999"), update("X", 5, true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- isolation (REQ-004, NFR-004) ----

    @Test
    void onlyTheOwnerCanUpdateACardAndOthersGet404() throws Exception {
        long idOfA = createCard(userA, "Nubank", 10);
        long idOfB = createCard(userB, "Nubank", 10);

        assertOnlyOwnerCanAccess(user ->
                json(put("/api/cards/" + (user.id() == userA.id() ? idOfA : idOfB)), update("Nubank", 10, true)));
    }

    @Test
    void anotherUsersChangeNeverTouchesMyCard() throws Exception {
        long idOfA = createCard(userA, "Nubank", 10);
        as(userB, json(put("/api/cards/" + idOfA), update("Invadido", 28, false))).andExpect(status().isNotFound());

        as(userA, get(LIST)).andExpect(jsonPath("$[0].name").value("Nubank"))
                .andExpect(jsonPath("$[0].dueDay").value(10)).andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void listsOfDifferentUsersNeverShareIds() throws Exception {
        createCard(userA, "Nubank", 10);
        createCard(userB, "Nubank", 10);
        assertThat(idsOf(userA, LIST)).doesNotContainAnyElementsOf(idsOf(userB, LIST));
    }

    @Test
    void everythingRequiresAuthentication() throws Exception {
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mvc.perform(json(post(LIST), create("X", 5))).andExpect(status().isUnauthorized());
        mvc.perform(json(put("/api/cards/1"), update("X", 5, true))).andExpect(status().isUnauthorized());
    }
}
