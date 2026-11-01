package br.com.nize.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/auth/register (TASK-004): REQ-001, NFR-005. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RegisterControllerTest {

    private static final String PASSWORD = "S3cret-pass!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MvcResult register(String name, String email, String password) throws Exception {
        String json = """
                {"name": "%s", "email": "%s", "password": "%s"}""".formatted(name, email, password);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    @Test
    void validRegistrationCreatesUserSettingsAndCurrentYear() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@example.com","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@example.com"));

        entityManager.flush(); // JdbcTemplate does not trigger JPA flushes
        Map<String, Object> user = jdbc.queryForMap(
                "SELECT id, onboarding_seen FROM app_user WHERE email = 'ana@example.com'");
        long userId = ((Number) user.get("id")).longValue();
        assertThat(user.get("onboarding_seen")).isEqualTo(false);

        Map<String, Object> settings = jdbc.queryForMap(
                "SELECT current_year, auto_installments FROM settings WHERE user_id = ?", userId);
        assertThat(((Number) settings.get("current_year")).intValue()).isEqualTo(Year.now().getValue());
        assertThat(settings.get("auto_installments")).isEqualTo(true);

        Integer years = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_year WHERE user_id = ? AND year_number = ?",
                Integer.class, userId, Year.now().getValue());
        assertThat(years).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsRejectedWithClearErrorIgnoringCase() throws Exception {
        assertThat(register("Bia", "bia@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(201);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bia 2","email":" BIA@Example.com ","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email = 'bia@example.com'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHashAndNeverReturnedOrLogged(CapturedOutput output) throws Exception {
        MvcResult result = register("Cris", "cris@example.com", PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE email = 'cris@example.com'", String.class);
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD).doesNotContain(hash).doesNotContainIgnoringCase("password");
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email = 'not-an-email'", Integer.class);
        assertThat(count).isZero();
    }
}
