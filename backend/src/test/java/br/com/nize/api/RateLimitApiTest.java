package br.com.nize.api;

import br.com.nize.service.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Attempt limits on /api/auth/** (REQ-047). Limits are lowered here; every test uses its own
 * e-mails and IPs because the limiter is shared by the whole Spring context.
 */
@SpringBootTest(properties = {
        "nize.rate-limit.login-per-email=5",
        "nize.rate-limit.login-per-ip=8",
        "nize.rate-limit.register-per-ip=3",
        "nize.rate-limit.forgot-per-email=3",
        "nize.rate-limit.forgot-per-ip=5",
        "nize.rate-limit.reset-per-ip=4"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class RateLimitApiTest {

    private static final String PASSWORD = "Right-password-1";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private EmailSender emailSender;

    private MvcResult call(String path, String json, String ip) throws Exception {
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON).content(json)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
        return mvc.perform(request).andReturn();
    }

    private void register(String email, String ip) throws Exception {
        MvcResult r = call("/api/auth/register",
                "{\"name\":\"Ana\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD), ip);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
    }

    private MvcResult login(String email, String password, String ip) throws Exception {
        return call("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), ip);
    }

    private static void assertBlocked(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(429);
        assertThat(r.getResponse().getContentAsString()).isEqualTo("{\"code\":\"TOO_MANY_ATTEMPTS\",\"fields\":{}}");
        assertThat(Long.parseLong(r.getResponse().getHeader("Retry-After"))).isPositive();
    }

    @Test
    void sixthLoginFailureForTheSameEmailIsBlockedEvenWithTheRightPassword() throws Exception {
        register("block@example.com", "10.0.1.1");
        for (int i = 0; i < 5; i++) {
            assertThat(login("block@example.com", "wrong-pass-" + i, "10.0.1." + (i + 2))
                    .getResponse().getStatus()).isEqualTo(401);
        }

        assertBlocked(login("block@example.com", PASSWORD, "10.0.1.20"));
        assertBlocked(login(" BLOCK@example.com ", PASSWORD, "10.0.1.21"));
    }

    @Test
    void unknownEmailIsBlockedExactlyLikeAKnownOne() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("ghost@example.com", "wrong-pass-" + i, "10.0.2." + i)
                    .getResponse().getStatus()).isEqualTo(401);
        }

        assertBlocked(login("ghost@example.com", "wrong-pass-x", "10.0.2.20"));
    }

    @Test
    void successfulLoginResetsTheEmailCounter() throws Exception {
        register("reset-counter@example.com", "10.0.3.1");
        for (int i = 0; i < 4; i++) {
            login("reset-counter@example.com", "wrong-pass-" + i, "10.0.3." + (i + 2));
        }
        assertThat(login("reset-counter@example.com", PASSWORD, "10.0.3.10").getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            assertThat(login("reset-counter@example.com", "wrong-pass-" + i, "10.0.3." + (i + 20))
                    .getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void oneIpTryingManyEmailsIsBlocked() throws Exception {
        for (int i = 0; i < 8; i++) {
            assertThat(login("spray" + i + "@example.com", "wrong-pass", "10.0.4.1")
                    .getResponse().getStatus()).isEqualTo(401);
        }

        assertBlocked(login("spray-last@example.com", "wrong-pass", "10.0.4.1"));
    }

    @Test
    void registrationIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            register("many" + i + "@example.com", "10.0.5.1");
        }

        assertBlocked(call("/api/auth/register",
                "{\"name\":\"Ana\",\"email\":\"many9@example.com\",\"password\":\"%s\"}".formatted(PASSWORD),
                "10.0.5.1"));
    }

    @Test
    void forgotPasswordIsLimitedPerEmailWhetherOrNotItExists() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("/api/auth/forgot-password", "{\"email\":\"nobody@example.com\"}", "10.0.6." + i)
                    .getResponse().getStatus()).isEqualTo(202);
        }

        assertBlocked(call("/api/auth/forgot-password", "{\"email\":\"nobody@example.com\"}", "10.0.6.20"));
    }

    @Test
    void forgotPasswordIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            call("/api/auth/forgot-password", "{\"email\":\"ip" + i + "@example.com\"}", "10.0.7.1");
        }

        assertBlocked(call("/api/auth/forgot-password", "{\"email\":\"ip9@example.com\"}", "10.0.7.1"));
    }

    @Test
    void badResetTokensAreLimitedPerIp() throws Exception {
        String body = "{\"token\":\"not-a-real-token\",\"newPassword\":\"Another-pass-1\"}";
        for (int i = 0; i < 4; i++) {
            assertThat(call("/api/auth/reset-password", body, "10.0.8.1").getResponse().getStatus()).isEqualTo(400);
        }

        assertBlocked(call("/api/auth/reset-password", body, "10.0.8.1"));
    }
}
