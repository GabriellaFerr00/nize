package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import br.com.nize.service.CategoryUsage;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Category deletion (TASK-018): REQ-012. The "has expenses" check goes through the CategoryUsage
 * contract, which Phase 3 implements; here it is simulated.
 */
class CategoryDeleteTest extends IsolationTestBase {

    private static final String LIST = "/api/years/" + Year.now().getValue() + "/categories";

    @MockitoBean
    private CategoryUsage usage;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private List<Integer> ids(TestUser user) throws Exception {
        String response = as(user, get(LIST)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$[*].id");
    }

    private int categoryExists(long id) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE id = ?", Integer.class, id);
    }

    @Test
    void categoryWithoutExpensesIsDeletedRightAway() throws Exception {
        long id = ids(userA).get(0);
        when(usage.countExpenses(userA.id(), id)).thenReturn(0L);

        as(userA, delete("/api/categories/" + id)).andExpect(status().isNoContent());

        assertThat(categoryExists(id)).isZero();
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(14));
        assertThat(ids(userA)).doesNotContain((int) id);
    }

    @Test
    void categoryWithExpensesIsNotDeletedAndTheCountIsReturned() throws Exception {
        long id = ids(userA).get(0);
        when(usage.countExpenses(userA.id(), id)).thenReturn(3L);

        as(userA, delete("/api/categories/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.count").value(3));

        assertThat(categoryExists(id)).isEqualTo(1);
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(15)).andExpect(jsonPath("$[0].name").value("Mercado"));
    }

    @Test
    void afterTheExpensesAreGoneTheCategoryCanBeDeleted() throws Exception {
        long id = ids(userA).get(0);
        when(usage.countExpenses(userA.id(), id)).thenReturn(2L, 0L);

        as(userA, delete("/api/categories/" + id)).andExpect(status().isConflict());
        as(userA, delete("/api/categories/" + id)).andExpect(status().isNoContent());
        assertThat(categoryExists(id)).isZero();
    }

    @Test
    void aDeactivatedCategoryWithoutExpensesCanStillBeDeleted() throws Exception {
        long id = ids(userA).get(0);
        as(userA, put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mercado\",\"active\":false}")).andExpect(status().isOk());

        as(userA, delete("/api/categories/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void deletingTwiceOrAnUnknownIdIs404() throws Exception {
        long id = ids(userA).get(0);
        as(userA, delete("/api/categories/" + id)).andExpect(status().isNoContent());
        as(userA, delete("/api/categories/" + id)).andExpect(status().isNotFound());
        as(userA, delete("/api/categories/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anotherUserGets404AndNothingIsDeleted() throws Exception {
        long idOfA = ids(userA).get(0);

        as(userB, delete("/api/categories/" + idOfA)).andExpect(status().isNotFound());

        assertThat(categoryExists(idOfA)).isEqualTo(1);
        as(userA, get(LIST)).andExpect(jsonPath("$.length()").value(15));
        // the usage check must not even run for someone else's category
        verify(usage, never()).countExpenses(eq(userB.id()), anyLong());
        verify(usage, never()).countExpenses(eq(userA.id()), eq(idOfA));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(delete("/api/categories/1")).andExpect(status().isUnauthorized());
    }
}
