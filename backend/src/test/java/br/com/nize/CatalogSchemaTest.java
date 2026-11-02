package br.com.nize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V2 migration (TASK-013): REQ-010, REQ-011, REQ-013, REQ-014. */
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class CatalogSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long userA;
    private long userB;

    @BeforeEach
    void createUsers() {
        userA = insertUser("a@catalog.test");
        userB = insertUser("b@catalog.test");
    }

    private long insertUser(String email) {
        jdbc.update("INSERT INTO app_user (name, email, password_hash, created_at) VALUES ('U', ?, 'h', CURRENT_TIMESTAMP)", email);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
    }

    private void insertCategory(long user, int year, String name) {
        jdbc.update("INSERT INTO category (user_id, year_number, name) VALUES (?, ?, ?)", user, year, name);
    }

    private void insertCard(long user, int year, String name, int dueDay) {
        jdbc.update("INSERT INTO card (user_id, year_number, name, due_day) VALUES (?, ?, ?, ?)", user, year, name, dueDay);
    }

    private void insertPerson(long user, int year, String name) {
        jdbc.update("INSERT INTO person (user_id, year_number, name) VALUES (?, ?, ?)", user, year, name);
    }

    @Test
    void tablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT LOWER(table_name) FROM information_schema.tables WHERE LOWER(table_schema) = 'public'",
                String.class);
        assertThat(tables).contains("category", "card", "person");
    }

    @Test
    void newRowsAreActiveByDefaultAndDescriptionIsOptional() {
        insertCategory(userA, 2026, "Mercado");
        insertCard(userA, 2026, "Nubank", 10);
        insertPerson(userA, 2026, "Mainha");

        assertThat(jdbc.queryForObject("SELECT active FROM category WHERE user_id = ?", Boolean.class, userA)).isTrue();
        assertThat(jdbc.queryForObject("SELECT description FROM category WHERE user_id = ?", String.class, userA)).isNull();
        assertThat(jdbc.queryForObject("SELECT active FROM card WHERE user_id = ?", Boolean.class, userA)).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM person WHERE user_id = ?", Boolean.class, userA)).isTrue();
    }

    @Test
    void repeatedNameInTheSameYearForTheSameUserFails() {
        insertCategory(userA, 2026, "Mercado");
        insertCard(userA, 2026, "Nubank", 10);
        insertPerson(userA, 2026, "Mainha");

        assertThatThrownBy(() -> insertCategory(userA, 2026, "Mercado")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCard(userA, 2026, "Nubank", 12)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPerson(userA, 2026, "Mainha")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameNameIsAllowedInAnotherYearOrForAnotherUser() {
        insertCategory(userA, 2026, "Mercado");
        insertCard(userA, 2026, "Nubank", 10);
        insertPerson(userA, 2026, "Mainha");

        insertCategory(userA, 2027, "Mercado");
        insertCard(userA, 2027, "Nubank", 10);
        insertPerson(userA, 2027, "Mainha");
        insertCategory(userB, 2026, "Mercado");
        insertCard(userB, 2026, "Nubank", 10);
        insertPerson(userB, 2026, "Mainha");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE name = 'Mercado'", Integer.class)).isEqualTo(3);
    }

    @Test
    void cardDueDayMustBeBetween1And31() {
        assertThatThrownBy(() -> insertCard(userA, 2026, "Zero", 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCard(userA, 2026, "TooHigh", 32)).isInstanceOf(DataIntegrityViolationException.class);
        insertCard(userA, 2026, "First", 1);
        insertCard(userA, 2026, "Last", 31);
    }

    @Test
    void everyRowBelongsToAnExistingUser() {
        assertThatThrownBy(() -> insertCategory(999_999L, 2026, "Orphan")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCard(999_999L, 2026, "Orphan", 5)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPerson(999_999L, 2026, "Orphan")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
