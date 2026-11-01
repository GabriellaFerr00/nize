package br.com.nize.api;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.nize.service.EmailSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Password recovery (TASK-006): REQ-003, NFR-005. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class PasswordResetTest {

    private static final String EMAIL = "fabi@example.com";
    private static final String OLD_PASSWORD = "Old-password-1";
    private static final String NEW_PASSWORD = "New-password-2";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void registerUser() throws Exception {
        json("/api/auth/register", """
                {"name":"Fabi","email":"%s","password":"%s"}""".formatted(EMAIL, OLD_PASSWORD))
                .andExpect(status().isCreated());
    }

    private ResultActions json(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions forgot(String email) throws Exception {
        return json("/api/auth/forgot-password", """
                {"email":"%s"}""".formatted(email));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return json("/api/auth/reset-password", """
                {"token":"%s","newPassword":"%s"}""".formatted(token, password));
    }

    private ResultActions login(String password) throws Exception {
        return json("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(EMAIL, password));
    }

    /** Requests a reset and returns the raw token the (mock) email sender received. */
    private String requestToken() throws Exception {
        forgot(EMAIL).andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce()).sendPasswordReset(eq(EMAIL), token.capture());
        return token.getValue();
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void requestStoresOnlyTheHashWithThirtyMinutesValidity() throws Exception {
        String token = requestToken();

        entityManager.flush();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT token_hash, expires_at, used_at FROM password_reset_token");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("token_hash")).isEqualTo(sha256(token)).isNotEqualTo(token);
        assertThat(rows.get(0).get("used_at")).isNull();
        Instant expiresAt = ((java.sql.Timestamp) rows.get(0).get("expires_at")).toInstant();
        assertThat(Duration.between(Instant.now(), expiresAt))
                .isBetween(Duration.ofMinutes(29), Duration.ofMinutes(31));
    }

    @Test
    void unknownEmailGetsTheSameResponseAndNoEmailIsSent() throws Exception {
        String known = forgot(EMAIL).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = forgot("ghost@example.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        verify(emailSender, never()).sendPasswordReset(eq("ghost@example.com"), anyString());
    }

    @Test
    void validTokenResetsThePasswordAndOldPasswordStopsWorking() throws Exception {
        String token = requestToken();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(NEW_PASSWORD).andExpect(status().isOk());
        login(OLD_PASSWORD).andExpect(status().isUnauthorized());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT used_at FROM password_reset_token", Object.class)).isNotNull();
    }

    @Test
    void tokenCannotBeReused() throws Exception {
        String token = requestToken();
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        reset(token, "Another-password-3")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRejectedAndPasswordIsUnchanged() throws Exception {
        String token = requestToken();
        entityManager.flush();
        jdbc.update("UPDATE password_reset_token SET expires_at = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))));
        entityManager.clear();

        reset(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownTokenGetsTheSameErrorAsExpiredOrUsed() throws Exception {
        reset("totally-unknown-token", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void weakNewPasswordIsRejectedWithoutConsumingTheToken() throws Exception {
        String token = requestToken();

        reset(token, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.newPassword").exists());

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aNewRequestInvalidatesThePreviousToken() throws Exception {
        String first = requestToken();
        entityManager.flush();
        forgot(EMAIL).andExpect(status().isAccepted());
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.times(2)).sendPasswordReset(eq(EMAIL), tokens.capture());
        String second = tokens.getAllValues().get(1);

        assertThat(second).isNotEqualTo(first);
        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }
}
