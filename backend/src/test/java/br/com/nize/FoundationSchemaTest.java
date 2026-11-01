package br.com.nize;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V1 migration (TASK-003): REQ-001, REQ-004, NFR-002. */
@SpringBootTest
@ActiveProfiles("h2")
class FoundationSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void foundationTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT LOWER(table_name) FROM information_schema.tables WHERE LOWER(table_schema) = 'public'",
                String.class);
        assertThat(tables).contains("app_user", "settings", "user_year", "password_reset_token");
    }

    @Test
    void noTotalOrBalanceColumnsArePersisted() {
        List<String> columns = jdbc.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = 'public'",
                String.class);
        assertThat(columns).noneMatch(c -> c.contains("total") || c.contains("balance") || c.contains("percent"));
    }

    @Test
    void duplicateEmailIsRejected() {
        String sql = "INSERT INTO app_user (name, email, password_hash, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        jdbc.update(sql, "Ana", "dup@example.com", "hash");
        assertThatThrownBy(() -> jdbc.update(sql, "Ana 2", "dup@example.com", "hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void settingsAndYearRequireAnExistingUser() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO settings (user_id, current_year) VALUES (?, ?)", 999_999L, 2026))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
