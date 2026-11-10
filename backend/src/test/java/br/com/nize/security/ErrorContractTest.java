package br.com.nize.security;

import br.com.nize.IsolationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Every error answers {code, fields} (ApiExceptionHandler) and never leaks SQL, class names or
 * stack traces — including framework errors and unexpected 500s.
 */
class ErrorContractTest extends IsolationTestBase {

    private void assertContract(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        MvcResult r = as(userA, request).andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).as(body).isEqualTo(status);
        assertThat(body).startsWith("{\"code\":\"" + code + "\",\"fields\":{");
        assertThat(body).doesNotContainIgnoringCase("exception").doesNotContainIgnoringCase("select")
                .doesNotContain("app_user", "br.com.nize", "timestamp", "trace");
    }

    @Test
    void sqlLookingParametersAreValidationErrors() throws Exception {
        assertContract(get("/api/categories").param("active", "x' OR '1'='1"), 400, "VALIDATION_ERROR");
        assertContract(get("/api/categories?active=1' OR '1'='1"), 400, "VALIDATION_ERROR");
        assertContract(put("/api/categories/1 UNION SELECT 1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                400, "VALIDATION_ERROR");
    }

    @Test
    void frameworkErrorsFollowTheContract() throws Exception {
        assertContract(get("/api/does-not-exist"), 404, "NOT_FOUND");
        assertContract(delete("/api/me"), 405, "METHOD_NOT_ALLOWED");
        assertContract(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"), 415,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        assertContract(get("/api/test/boom"), 500, "INTERNAL_ERROR");
    }
}
