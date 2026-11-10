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

/** Category CRUD (TASK-015): REQ-010, REQ-011, REQ-004, NFR-004. */
class CategoryApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String LIST = "/api/categories";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private String body(String name, String description, Boolean active) {
        StringBuilder json = new StringBuilder("{\"name\":" + quote(name));
        if (description != null) json.append(",\"description\":").append(quote(description));
        if (active != null) json.append(",\"active\":").append(active);
        return json.append("}").toString();
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String content) {
        return request.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private List<Integer> idsOf(TestUser user, String url) throws Exception {
        String response = as(user, get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$[*].id");
    }

    private long firstCategoryId(TestUser user) throws Exception {
        return idsOf(user, LIST).get(0);
    }

    // ---- list ----

    @Test
    void listReturnsTheFifteenFactoryCategoriesInOrderWithTheirFields() throws Exception {
        as(userA, get(LIST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].name").value("Mercado"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[14].name").value("Contas"));
    }

    // ---- create ----

    @Test
    void createAddsACategoryToTheList() throws Exception {
        as(userA, json(post(LIST), body("  Pets  ", "Ração e veterinário", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Pets"))
                .andExpect(jsonPath("$.description").value("Ração e veterinário"))
                .andExpect(jsonPath("$.active").value(true));

        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(16)).andExpect(jsonPath("$[15].name").value("Pets"));
        as(userB, get(LIST)).andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void blankDescriptionIsStoredAsNoDescription() throws Exception {
        as(userA, json(post(LIST), body("Pets", "   ", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void invalidNamesAreRejected() throws Exception {
        as(userA, json(post(LIST), body("", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.name").exists());
        as(userA, json(post(LIST), body("x".repeat(81), null, null))).andExpect(status().isBadRequest());
        as(userA, json(post(LIST), body("Ok", "d".repeat(256), null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.description").exists());
    }

    @Test
    void aRepeatedNameIsRejectedIgnoringCase() throws Exception {
        as(userA, json(post(LIST), body("mercado", null, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void theSameNameIsFineForAnotherUser() throws Exception {
        as(userB, json(post(LIST), body("Pets", null, null))).andExpect(status().isCreated());
        as(userA, json(post(LIST), body("Pets", null, null))).andExpect(status().isCreated());
    }

    // ---- update ----

    @Test
    void renameAndDescribe() throws Exception {
        long id = firstCategoryId(userA);
        as(userA, json(put("/api/categories/" + id), body("Feira e mercado", "Compras da semana", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Feira e mercado"))
                .andExpect(jsonPath("$.description").value("Compras da semana"));

        as(userA, get(LIST)).andExpect(jsonPath("$[0].name").value("Feira e mercado"));
    }

    @Test
    void renamingToAnExistingNameIsRejectedButKeepingOrRecasingItIsFine() throws Exception {
        long mercado = firstCategoryId(userA);
        as(userA, json(put("/api/categories/" + mercado), body("Lazer", null, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));

        as(userA, json(put("/api/categories/" + mercado), body("Mercado", "nova descrição", true))).andExpect(status().isOk());
        as(userA, json(put("/api/categories/" + mercado), body("MERCADO", null, true))).andExpect(status().isOk());
    }

    @Test
    void deactivatedCategoriesLeaveTheActiveListButStayInTheFullList() throws Exception {
        long id = firstCategoryId(userA);
        as(userA, json(put("/api/categories/" + id), body("Mercado", null, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        as(userA, get(LIST + "?active=true")).andExpect(jsonPath("$.length()").value(14));
        assertThat(idsOf(userA, LIST + "?active=true")).doesNotContain((int) id);
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(15));

        as(userA, json(put("/api/categories/" + id), body("Mercado", null, true))).andExpect(status().isOk());
        as(userA, get(LIST + "?active=true")).andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void updateRequiresTheActiveFlag() throws Exception {
        long id = firstCategoryId(userA);
        as(userA, json(put("/api/categories/" + id), body("Mercado", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.active").exists());
    }

    @Test
    void unknownCategoryIs404() throws Exception {
        as(userA, json(put("/api/categories/999999"), body("X", null, true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- isolation (REQ-004, NFR-004) ----

    @Test
    void onlyTheOwnerCanUpdateACategoryAndOthersGet404() throws Exception {
        long idOfA = firstCategoryId(userA);
        long idOfB = firstCategoryId(userB);
        assertThat(idOfA).isNotEqualTo(idOfB);

        assertOnlyOwnerCanAccess(user -> {
            long id = user.id() == userA.id() ? idOfA : idOfB;
            return json(put("/api/categories/" + id), body("Mercado", null, true));
        });
    }

    @Test
    void anotherUsersChangeNeverTouchesMyCategory() throws Exception {
        long idOfA = firstCategoryId(userA);
        as(userB, json(put("/api/categories/" + idOfA), body("Invadida", "x", false))).andExpect(status().isNotFound());

        as(userA, get(LIST)).andExpect(jsonPath("$[0].name").value("Mercado")).andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void listsOfDifferentUsersNeverShareIds() throws Exception {
        assertThat(idsOf(userA, LIST)).doesNotContainAnyElementsOf(idsOf(userB, LIST));
    }

    @Test
    void everythingRequiresAuthentication() throws Exception {
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mvc.perform(json(post(LIST), body("X", null, null))).andExpect(status().isUnauthorized());
        mvc.perform(json(put("/api/categories/1"), body("X", null, true))).andExpect(status().isUnauthorized());
    }
}
