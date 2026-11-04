package br.com.nize.security;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security pipeline checks for authentication (NFR-005, REQ-002, D-5, D-18). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class AuthHardeningTest {

    private static final String SECRET = "test-only-secret-test-only-secret-0123456789";
    private static final String PASSWORD = "Hardening-pass-1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    private long userId;

    @BeforeEach
    void registerUser() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lia\",\"email\":\"lia@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        userId = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(JwtClaimsSet claims) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private JwtClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().issuer("nize").subject(String.valueOf(userId))
                .issuedAt(now).expiresAt(now.plus(Duration.ofHours(1)));
    }

    private void assertRejected(String token) throws Exception {
        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() throws Exception {
        long exp = Instant.now().plus(Duration.ofHours(1)).getEpochSecond();
        String none = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + b64("{\"iss\":\"nize\",\"sub\":\"%d\",\"exp\":%d}".formatted(userId, exp)) + ".";

        assertRejected(none);
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String[] parts = sign(validClaims().build()).split("\\.");
        long exp = Instant.now().plus(Duration.ofHours(1)).getEpochSecond();
        String forged = parts[0] + "." + b64("{\"iss\":\"nize\",\"sub\":\"%d\",\"exp\":%d}".formatted(userId + 1, exp))
                + "." + parts[2];

        assertRejected(forged);
    }

    @Test
    void wrongIssuerAndMissingExpiryAreRejected() throws Exception {
        assertRejected(sign(validClaims().issuer("someone-else").build()));
        assertRejected(sign(JwtClaimsSet.builder().issuer("nize").subject(String.valueOf(userId))
                .issuedAt(Instant.now()).build()));
        // sanity: the same helper with valid claims is accepted
        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + sign(validClaims().build())))
                .andExpect(status().isOk()).andExpect(content().string(String.valueOf(userId)));
    }

    @Test
    void passwordsUseBcryptWithCostAtLeastTen() {
        Matcher m = Pattern.compile("^\\$2[aby]?\\$(\\d{2})\\$").matcher(passwordEncoder.encode("any-password-1"));

        assertThat(m.find()).isTrue();
        assertThat(Integer.parseInt(m.group(1))).isGreaterThanOrEqualTo(10);
    }

    @Test
    void unknownEmailTakesAboutAsLongAsAWrongPassword() throws Exception {
        long unknown = medianLoginNanos("nobody-here@example.com");
        long wrong = medianLoginNanos("lia@example.com");

        // Both paths run one BCrypt comparison; a large gap would let attackers enumerate e-mails.
        assertThat((double) unknown / wrong).isBetween(0.5, 2.0);
    }

    private long medianLoginNanos(String email) throws Exception {
        long[] times = new long[7];
        for (int i = 0; i < times.length; i++) {
            long start = System.nanoTime();
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"wrong-password-%d\"}".formatted(email, i)))
                    .andExpect(status().isUnauthorized());
            times[i] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        return times[times.length / 2];
    }

    @Test
    void onlyTheHealthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/info", "/actuator/beans"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isIn(401, 404);
        }
    }

    @Test
    void invalidRequestFollowsTheErrorContractUsedByTheSmokeTest() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields").isMap());
    }
}
