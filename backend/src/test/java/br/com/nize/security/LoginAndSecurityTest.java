package br.com.nize.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login + JWT filter (TASK-005): REQ-002, NFR-005, D-5, D-6. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class LoginAndSecurityTest {

    private static final String PASSWORD = "S3cret-pass!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtDecoder decoder;
    private long userId;

    @BeforeEach
    void registerUser() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Dani","email":"dani@example.com","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        userId = ((Number) com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password))).andReturn();
        return r.getResponse().getContentAsString() + "|" + r.getResponse().getStatus();
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dani@example.com","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void correctCredentialsReturnTokenWithUserIdAndOneHourExpiry() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" DANI@example.com ","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.password").doesNotExist());

        Jwt jwt = decoder.decode(token());
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
        String wrongPassword = login("dani@example.com", "wrong-password");
        String unknownEmail = login("nobody@example.com", PASSWORD);

        assertThat(wrongPassword).isEqualTo(unknownEmail).endsWith("|401").contains("INVALID_CREDENTIALS");
    }

    @Test
    void blankLoginFieldsAreValidationErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void protectedRouteWithoutOrWithBadTokenIs401() throws Exception {
        mvc.perform(get("/api/test/whoami")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenGivesAccessAndUserIdComesFromTheToken() throws Exception {
        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(userId)));
    }

    @Test
    void expiredTokenAndTokenSignedWithAnotherSecretAreRejected() throws Exception {
        Instant now = Instant.now();
        String expired = sign("test-only-secret-test-only-secret-0123456789",
                now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)));
        String foreign = sign("another-secret-another-secret-0123456789xx",
                now, now.plus(Duration.ofHours(1)));
        String good = sign("test-only-secret-test-only-secret-0123456789", now, now.plus(Duration.ofHours(1)));

        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
        // sanity: the helper itself produces a token the app accepts
        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + good))
                .andExpect(status().isOk());
    }

    private String sign(String secret, Instant issuedAt, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("nize").subject(String.valueOf(userId))
                .issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
