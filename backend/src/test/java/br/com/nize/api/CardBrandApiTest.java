package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Year;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Card brands used in Brazil (TASK-070, REQ-052, D-21): stored as a key, optional, copied to a new year. */
class CardBrandApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String LIST = "/api/cards";

    private long create(TestUser user, String json) throws Exception {
        String body = as(user, post(LIST).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void aCardTakesABrandOfTheListOrNone() throws Exception {
        create(userA, "{\"name\":\"Roxinho\",\"dueDay\":10,\"brand\":\"nubank\"}");
        create(userA, "{\"name\":\"Loja\",\"dueDay\":5}");

        as(userA, get(LIST))
                .andExpect(jsonPath("$[?(@.name == 'Roxinho')].brand").value("nubank"))
                .andExpect(jsonPath("$[?(@.name == 'Loja')].brand").value((Object) null));
    }

    @Test
    void editingChangesOrRemovesTheBrand() throws Exception {
        long id = create(userA, "{\"name\":\"Meu\",\"dueDay\":10,\"brand\":\"itau\"}");

        as(userA, put("/api/cards/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Meu\",\"dueDay\":10,\"active\":true,\"brand\":\"bb\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brand").value("bb"));
        as(userA, put("/api/cards/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Meu\",\"dueDay\":10,\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brand").value((Object) null));
    }

    @Test
    void aBrandOutsideTheListIsRefused() throws Exception {
        for (String bad : List.of("\"Nubank\"", "\"banco-x\"", "\"\"", "\"<script>\"")) {
            as(userA, post(LIST).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"X\",\"dueDay\":10,\"brand\":%s}".formatted(bad)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void aNewYearKeepsTheBrand() throws Exception {
        create(userA, "{\"name\":\"Roxinho\",\"dueDay\":10,\"brand\":\"nubank\"}");

        as(userA, post("/api/years/" + (YEAR + 1))).andExpect(status().isCreated());

        as(userA, get("/api/cards")).andExpect(jsonPath("$[0].brand").value("nubank"));
    }

    @Test
    void anotherUserCannotChangeMyBrand() throws Exception {
        long id = create(userA, "{\"name\":\"Meu\",\"dueDay\":10,\"brand\":\"itau\"}");

        as(userB, put("/api/cards/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Meu\",\"dueDay\":10,\"active\":true,\"brand\":\"inter\"}"))
                .andExpect(status().isNotFound());
        as(userA, get(LIST)).andExpect(jsonPath("$[0].brand").value("itau"));
    }
}
