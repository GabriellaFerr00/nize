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

/** Colors of categories and people from the Nize palette (TASK-069, REQ-052, D-21). */
class CategoryAndPersonColorTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();

    private long categoryId(TestUser user, String name) throws Exception {
        String body = as(user, get("/api/years/" + YEAR + "/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0);
    }

    @Test
    void aCategoryTakesAColorOfThePaletteAndCanLoseIt() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        as(userA, get("/api/years/" + YEAR + "/categories")).andExpect(jsonPath("$[?(@.name == 'Mercado')].color").isEmpty());

        as(userA, put("/api/categories/" + mercado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mercado\",\"active\":true,\"color\":\"teal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").value("teal"));

        as(userA, put("/api/categories/" + mercado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mercado\",\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").doesNotExist());
    }

    @Test
    void aPersonTakesAColorAtCreationAndOnEdit() throws Exception {
        String created = as(userA, post("/api/years/" + YEAR + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mainha\",\"color\":\"pink\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.color").value("pink"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        as(userA, put("/api/people/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mainha\",\"active\":true,\"color\":\"purple\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").value("purple"));
    }

    @Test
    void aColorOutsideThePaletteIsRefused() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        for (String bad : List.of("\"#ff0000\"", "\"GREEN\"", "\"black\"", "\"\"")) {
            as(userA, put("/api/categories/" + mercado).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Mercado\",\"active\":true,\"color\":%s}".formatted(bad)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        as(userA, post("/api/years/" + YEAR + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"color\":\"url(javascript:1)\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNewYearKeepsTheColors() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        as(userA, put("/api/categories/" + mercado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mercado\",\"active\":true,\"color\":\"blue\"}")).andExpect(status().isOk());
        as(userA, post("/api/years/" + YEAR + "/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mainha\",\"color\":\"pink\"}")).andExpect(status().isCreated());

        as(userA, post("/api/years/" + (YEAR + 1))).andExpect(status().isCreated());

        as(userA, get("/api/years/" + (YEAR + 1) + "/categories"))
                .andExpect(jsonPath("$[?(@.name == 'Mercado')].color").value("blue"));
        as(userA, get("/api/years/" + (YEAR + 1) + "/people")).andExpect(jsonPath("$[0].color").value("pink"));
    }

    @Test
    void anotherUserCannotColorMyCategory() throws Exception {
        long mercado = categoryId(userA, "Mercado");
        as(userB, put("/api/categories/" + mercado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mercado\",\"active\":true,\"color\":\"red\"}"))
                .andExpect(status().isNotFound());
    }
}
