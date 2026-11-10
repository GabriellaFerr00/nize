package br.com.nize;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for tenant-isolation tests (REQ-004, NFR-004): two registered users, each with a token,
 * and a helper that checks "owner gets 2xx, the other user gets 404" for any endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
public abstract class IsolationTestBase {

    public static final String PASSWORD = "S3cret-pass!";

    public record TestUser(long id, String email, String token) {
    }

    @Autowired
    protected MockMvc mvc;

    protected TestUser userA;
    protected TestUser userB;

    private final java.util.Map<String, Long> testCards = new java.util.HashMap<>();

    /**
     * Since 2026-11-09 every credit expense needs a card (REQ-015/016). Tests that are not about cards call this on
     * their JSON: a credit expense without {@code cardId} gets the user's test card of that year (due on the 31st,
     * so it never moves an expense to an earlier closing interval).
     */
    protected String withCard(TestUser user, Object month, String json) throws Exception {
        boolean credit = json.contains("\"type\":\"CARD\"") || json.contains("\"paymentMethod\":\"CREDIT\"");
        if (!credit || json.contains("\"cardId\"")) {
            return json;
        }
        int year = Integer.parseInt(month.toString().substring(0, 4));
        String key = user.id() + "/" + year;
        Long card = testCards.get(key);
        if (card == null) {
            String body = as(user, post("/api/years/" + year + "/cards").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Cartão de teste\",\"dueDay\":31}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            card = ((Number) JsonPath.read(body, "$.id")).longValue();
            testCards.put(key, card);
        }
        return json.replaceFirst("\\{", "{\"cardId\":" + card + ",");
    }

    @BeforeEach
    void createTwoUsers() throws Exception {
        userA = createUser("Ana", "user.a@example.com");
        userB = createUser("Beto", "user.b@example.com");
    }

    private TestUser createUser(String name, String email) throws Exception {
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"%s"}""".formatted(name, email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new TestUser(((Number) JsonPath.read(registered, "$.id")).longValue(), email,
                JsonPath.read(login, "$.token"));
    }

    /** Performs the request authenticated as the given user. */
    protected ResultActions as(TestUser user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + user.token()));
    }

    /**
     * {@code requestForResourceOf} builds the request that targets the given user's resource.
     * The owner must succeed (2xx) and the other user must get 404.
     */
    protected void assertOnlyOwnerCanAccess(Function<TestUser, MockHttpServletRequestBuilder> requestForResourceOf)
            throws Exception {
        as(userA, requestForResourceOf.apply(userA)).andExpect(status().is2xxSuccessful());
        as(userB, requestForResourceOf.apply(userA)).andExpect(status().isNotFound());
        as(userB, requestForResourceOf.apply(userB)).andExpect(status().is2xxSuccessful());
        as(userA, requestForResourceOf.apply(userB)).andExpect(status().isNotFound());
    }
}
