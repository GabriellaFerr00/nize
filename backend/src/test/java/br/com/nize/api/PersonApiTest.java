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

/** Person CRUD (TASK-017): REQ-014, REQ-004, NFR-004. People are third parties who use the user's card. */
class PersonApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String LIST = "/api/people";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private String create(String name) {
        return "{\"name\":\"%s\"}".formatted(name);
    }

    private String update(String name, Object active) {
        return "{\"name\":\"%s\",\"active\":%s}".formatted(name, active);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String content) {
        return request.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private long createPerson(TestUser user, String name) throws Exception {
        String response = as(user, json(post(LIST), create(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private List<Integer> idsOf(TestUser user, String url) throws Exception {
        String response = as(user, get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$[*].id");
    }

    @Test
    void aNewAccountHasNoPeopleYet() throws Exception {
        as(userA, get(LIST)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createAddsAPersonToTheList() throws Exception {
        as(userA, json(post(LIST), create("  Mainha  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Mainha"))
                .andExpect(jsonPath("$.active").value(true));

        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Mainha"));
        as(userB, get(LIST)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nameIsRequiredAndLimitedTo80() throws Exception {
        as(userA, json(post(LIST), create("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.name").exists());
        as(userA, json(post(LIST), create("x".repeat(81)))).andExpect(status().isBadRequest());
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aRepeatedNameIsRejectedIgnoringCase() throws Exception {
        createPerson(userA, "Mainha");
        as(userA, json(post(LIST), create("MAINHA")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERSON_NAME_TAKEN"));
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theSameNameIsFineForAnotherUser() throws Exception {
        createPerson(userA, "Mainha");
        createPerson(userB, "Mainha");
    }

    @Test
    void rename() throws Exception {
        long id = createPerson(userA, "Mainha");
        as(userA, json(put("/api/people/" + id), update("Mãe", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.name").value("Mãe"));
        as(userA, get(LIST)).andExpect(jsonPath("$[0].name").value("Mãe"));
    }

    @Test
    void renamingToAnExistingNameIsRejectedButKeepingOrRecasingItIsFine() throws Exception {
        long mainha = createPerson(userA, "Mainha");
        createPerson(userA, "Joana");

        as(userA, json(put("/api/people/" + mainha), update("joana", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERSON_NAME_TAKEN"));
        as(userA, json(put("/api/people/" + mainha), update("Mainha", true))).andExpect(status().isOk());
        as(userA, json(put("/api/people/" + mainha), update("MAINHA", true))).andExpect(status().isOk());
    }

    @Test
    void deactivatingKeepsTheRecordAndHidesItFromTheActiveList() throws Exception {
        long id = createPerson(userA, "Mainha");
        createPerson(userA, "Joana");

        as(userA, json(put("/api/people/" + id), update("Mainha", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        assertThat(idsOf(userA, LIST + "?active=true")).doesNotContain((int) id).hasSize(1);
        assertThat(idsOf(userA, LIST)).contains((int) id).hasSize(2);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE id = ?", Integer.class, id)).isEqualTo(1);

        as(userA, json(put("/api/people/" + id), update("Mainha", true))).andExpect(status().isOk());
        assertThat(idsOf(userA, LIST + "?active=true")).hasSize(2);
    }

    @Test
    void updateRequiresTheActiveFlagAndKnownIds() throws Exception {
        long id = createPerson(userA, "Mainha");
        as(userA, json(put("/api/people/" + id), "{\"name\":\"Mainha\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.active").exists());
        as(userA, json(put("/api/people/999999"), update("X", true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void onlyTheOwnerCanUpdateAPersonAndOthersGet404() throws Exception {
        long idOfA = createPerson(userA, "Mainha");
        long idOfB = createPerson(userB, "Mainha");

        assertOnlyOwnerCanAccess(user ->
                json(put("/api/people/" + (user.id() == userA.id() ? idOfA : idOfB)), update("Mainha", true)));
    }

    @Test
    void anotherUsersChangeNeverTouchesMyPerson() throws Exception {
        long idOfA = createPerson(userA, "Mainha");
        as(userB, json(put("/api/people/" + idOfA), update("Invadida", false))).andExpect(status().isNotFound());

        as(userA, get(LIST)).andExpect(jsonPath("$[0].name").value("Mainha")).andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void listsOfDifferentUsersNeverShareIds() throws Exception {
        createPerson(userA, "Mainha");
        createPerson(userB, "Mainha");
        assertThat(idsOf(userA, LIST)).doesNotContainAnyElementsOf(idsOf(userB, LIST));
    }

    @Test
    void everythingRequiresAuthentication() throws Exception {
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mvc.perform(json(post(LIST), create("X"))).andExpect(status().isUnauthorized());
        mvc.perform(json(put("/api/people/1"), update("X", true))).andExpect(status().isUnauthorized());
    }
}
