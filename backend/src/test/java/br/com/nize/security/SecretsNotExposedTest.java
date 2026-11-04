package br.com.nize.security;

import br.com.nize.service.EmailSender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * NFR-008: passwords, tokens and the JWT secret never show up in logs or API responses.
 * The e-mail sender is mocked: the dev {@code LoggingEmailSender} logs the reset token on purpose
 * and is never active in prod (D-17).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SecretsNotExposedTest {

    private static final String PASSWORD = "Never-logged-pass-7";
    private static final String NEW_PASSWORD = "Never-logged-new-8";
    private static final String JWT_SECRET = "test-only-secret-test-only-secret-0123456789";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private EmailSender emailSender;

    private String send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void authFlowsKeepSecretsOutOfLogsAndResponses(CapturedOutput output) throws Exception {
        StringBuilder responses = new StringBuilder();
        responses.append(send(json("/api/auth/register",
                "{\"name\":\"Rui\",\"email\":\"rui@example.com\",\"password\":\"%s\"}".formatted(PASSWORD))));
        responses.append(send(json("/api/auth/login",
                "{\"email\":\"rui@example.com\",\"password\":\"wrong-%s\"}".formatted(PASSWORD))));
        String login = send(json("/api/auth/login",
                "{\"email\":\"rui@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)));
        String token = JsonPath.read(login, "$.token");
        responses.append(send(get("/api/me").header("Authorization", "Bearer " + token)));
        responses.append(send(json("/api/auth/forgot-password", "{\"email\":\"rui@example.com\"}")));
        responses.append(send(json("/api/auth/reset-password",
                "{\"token\":\"made-up-token-value-123\",\"newPassword\":\"%s\"}".formatted(NEW_PASSWORD))));

        assertThat(responses.toString()).doesNotContain(PASSWORD, NEW_PASSWORD, JWT_SECRET, "made-up-token-value-123");
        assertThat(output.getAll()).doesNotContain(PASSWORD, NEW_PASSWORD, JWT_SECRET, token, "made-up-token-value-123");
    }
}
