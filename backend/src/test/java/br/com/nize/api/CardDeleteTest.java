package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Card deletion (TASK-063): REQ-013, REQ-004. A card used by any expense is kept and the count is returned. */
class CardDeleteTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String LIST = "/api/cards";
    private static final YearMonth APRIL = YearMonth.of(YEAR, 4);
    private static final YearMonth MAY = YearMonth.of(YEAR, 5);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createCard(TestUser user, String name) throws Exception {
        return idOf(as(user, post(LIST).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"dueDay\":10}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long categoryId(TestUser user) throws Exception {
        String body = as(user, get("/api/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[*].id");
        return ids.get(0);
    }

    private void cardExpense(TestUser user, YearMonth month, long cardId) throws Exception {
        as(user, post("/api/months/" + month + "/expenses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Compra\",\"categoryId\":%d,\"amountCents\":1000,\"type\":\"CARD\",\"date\":\"%s\",\"cardId\":%d}"
                        .formatted(categoryId(user), month.atDay(3), cardId)))
                .andExpect(status().isCreated());
    }

    private int cardExists(long id) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM card WHERE id = ?", Integer.class, id);
    }

    @Test
    void aCardWithoutExpensesIsDeletedRightAway() throws Exception {
        long id = createCard(userA, "Nubank");

        as(userA, delete("/api/cards/" + id)).andExpect(status().isNoContent());

        assertThat(cardExists(id)).isZero();
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aCardUsedByExpensesIsKeptAndTheCountIsReturned() throws Exception {
        long id = createCard(userA, "Nubank");
        cardExpense(userA, APRIL, id);
        cardExpense(userA, MAY, id);

        as(userA, delete("/api/cards/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARD_IN_USE"))
                .andExpect(jsonPath("$.count").value(2));

        assertThat(cardExists(id)).isEqualTo(1);
    }

    @Test
    void anotherUsersExpensesDoNotCount() throws Exception {
        long idOfA = createCard(userA, "Nubank");
        long idOfB = createCard(userB, "Nubank");
        cardExpense(userB, APRIL, idOfB);

        as(userA, delete("/api/cards/" + idOfA)).andExpect(status().isNoContent());
    }

    @Test
    void onlyTheOwnerCanDeleteACardAndOthersGet404() throws Exception {
        long idOfA = createCard(userA, "Nubank");
        long idOfB = createCard(userB, "Nubank");

        assertOnlyOwnerCanAccess(user -> delete("/api/cards/" + (user.id() == userA.id() ? idOfA : idOfB)));
        assertThat(cardExists(idOfA)).isZero();
    }

    @Test
    void anotherUserCannotDeleteMyCard() throws Exception {
        long idOfA = createCard(userA, "Nubank");

        as(userB, delete("/api/cards/" + idOfA)).andExpect(status().isNotFound());

        assertThat(cardExists(idOfA)).isEqualTo(1);
    }
}
