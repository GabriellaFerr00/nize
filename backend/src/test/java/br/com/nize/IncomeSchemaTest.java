package br.com.nize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V5 migration (TASK-033): table income (REQ-024..026, NFR-001, NFR-002). */
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class IncomeSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long user;
    private long person;

    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO app_user (name, email, password_hash, created_at) VALUES ('U', 'u@income.test', 'h', CURRENT_TIMESTAMP)");
        user = jdbc.queryForObject("SELECT id FROM app_user WHERE email = 'u@income.test'", Long.class);
        jdbc.update("INSERT INTO person (user_id, year_number, name) VALUES (?, 2026, 'Mainha')", user);
        person = jdbc.queryForObject("SELECT id FROM person WHERE user_id = ?", Long.class, user);
    }

    private void insert(long amount, Long personId, Integer number, Integer count, String group) {
        jdbc.update("""
                INSERT INTO income (user_id, month_ref, name, income_date, amount, person_id,
                                    installment_number, installment_count, installment_group_id)
                VALUES (?, '2026-11', 'Salário', DATE '2026-11-05', ?, ?, ?, ?, ?)
                """, user, amount, personId, number, count, group);
    }

    @Test
    void tableExistsWithMoneyAsDecimalInReaisAndNoDerivedColumns() {
        // D-2: DECIMAL(12,2) in reais (the back-end converts to cents)
        java.util.Map<String, Object> column = jdbc.queryForMap("""
                SELECT data_type, numeric_precision, numeric_scale FROM information_schema.columns
                WHERE LOWER(table_name) = 'income' AND LOWER(column_name) = 'amount'""");
        assertThat(String.valueOf(column.get("data_type"))).isIn("DECIMAL", "NUMERIC", "decimal", "numeric");
        assertThat(((Number) column.get("numeric_precision")).intValue()).isEqualTo(12);
        assertThat(((Number) column.get("numeric_scale")).intValue()).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE LOWER(table_name) = 'income'
                  AND (LOWER(column_name) LIKE '%total%' OR LOWER(column_name) LIKE '%balance%')""", String.class))
                .isEmpty();
    }

    @Test
    void acceptsPlainIncomeReimbursementAndInstallment() {
        insert(500_000, null, null, null, null);
        insert(19_000, person, null, null, null);
        insert(10_000, null, 1, 3, "g-1");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM income WHERE user_id = ?", Integer.class, user)).isEqualTo(3);
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> insert(0, null, null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void installmentNumberAndCountComeTogetherAndInOrder() {
        assertThatThrownBy(() -> insert(100, null, 1, null, "g")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(100, null, 4, 3, "g")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInstallmentOfAGroupExistsOnlyOnce() {
        insert(100, null, 2, 3, "g-2");
        assertThatThrownBy(() -> insert(100, null, 2, 3, "g-2")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void personMustExist() {
        assertThatThrownBy(() -> insert(100, 999_999L, null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
