package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import br.com.nize.service.CategoryUsage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With the real (pre-Phase-3) CategoryUsage, no category has expenses, so deletion just works. */
class CategoryDeleteDefaultUsageTest extends IsolationTestBase {

    @Autowired
    private CategoryUsage usage;

    @Test
    void defaultUsageReportsNoExpensesAndDeletionSucceeds() throws Exception {
        String body = as(userA, get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$[0].id")).longValue();

        assertThat(usage.countExpenses(userA.id(), id)).isZero();
        as(userA, delete("/api/categories/" + id)).andExpect(status().isNoContent());
    }
}
