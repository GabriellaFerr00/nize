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

/** V3 migration (TASK-023): table expense (REQ-015..021, NFR-001, NFR-002). */
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class ExpenseSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long user;
    private long category;
    private long card;
    private long person;

    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO app_user (name, email, password_hash, created_at) VALUES ('U', 'u@expense.test', 'h', CURRENT_TIMESTAMP)");
        user = jdbc.queryForObject("SELECT id FROM app_user WHERE email = 'u@expense.test'", Long.class);
        jdbc.update("INSERT INTO category (user_id, year_number, name) VALUES (?, 2026, 'Mercado')", user);
        category = jdbc.queryForObject("SELECT id FROM category WHERE user_id = ?", Long.class, user);
        jdbc.update("INSERT INTO card (user_id, year_number, name, due_day) VALUES (?, 2026, 'Nubank', 10)", user);
        card = jdbc.queryForObject("SELECT id FROM card WHERE user_id = ?", Long.class, user);
        jdbc.update("INSERT INTO person (user_id, year_number, name) VALUES (?, 2026, 'Mainha')", user);
        person = jdbc.queryForObject("SELECT id FROM person WHERE user_id = ?", Long.class, user);
    }

    private void insert(long amount, String type, String payment, Long cardId, Long personId,
                        Integer number, Integer count, String group) {
        jdbc.update("""
                INSERT INTO expense (user_id, month_ref, name, category_id, amount_cents, type, payment_method, card_id,
                                     person_id, expense_date, installment_number, installment_count, installment_group_id)
                VALUES (?, '2026-11', 'x', ?, ?, ?, ?, ?, ?, DATE '2026-11-05', ?, ?, ?)
                """, user, category, amount, type, payment, cardId, personId, number, count, group);
    }

    private void simple(long amount, String type, String payment) {
        insert(amount, type, payment, null, null, null, null, null);
    }

    @Test
    void tableExistsWithMoneyAsBigintAndNoDerivedColumns() {
        String type = jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE LOWER(table_name) = 'expense' AND LOWER(column_name) = 'amount_cents'""", String.class);
        assertThat(type).isEqualToIgnoringCase("BIGINT");
        assertThat(jdbc.queryForList("""
                SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_name) = 'expense'""", String.class))
                .noneMatch(c -> c.contains("total") || c.contains("balance") || c.contains("percent"));
    }

    @Test
    void aValidFixedPixAndCardCreditExpenseAreAccepted() {
        simple(43_500, "FIXED", "PIX");
        insert(50_000, "CARD", "CREDIT", card, null, null, null, null);
        assertThat(jdbc.queryForObject("SELECT paid FROM expense WHERE type = 'FIXED'", Boolean.class)).isFalse();
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> simple(0, "FIXED", "PIX")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> simple(-5, "FIXED", "PIX")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void typeAndPaymentMethodMustBeKnownValues() {
        assertThatThrownBy(() -> simple(100, "OTHER", "PIX")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> simple(100, "FIXED", "CASH")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aFixedExpenseMayBePaidOnCreditButACardExpenseMayNotBePix() {
        simple(3_990, "FIXED", "CREDIT"); // subscription on the card
        assertThatThrownBy(() -> simple(100, "CARD", "PIX")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCardOnlyMakesSenseWithCredit() {
        assertThatThrownBy(() -> insert(100, "FIXED", "PIX", card, null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        insert(100, "FIXED", "CREDIT", card, null, null, null, null);
        insert(100, "CARD", "CREDIT", null, null, null, null, null); // "Sem cartão"
    }

    @Test
    void installmentNumberAndCountAreBothSetOrBothEmptyAndOrdered() {
        insert(100, "CARD", "CREDIT", card, null, 7, 12, null);
        assertThatThrownBy(() -> insert(100, "CARD", "CREDIT", card, null, 7, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(100, "CARD", "CREDIT", card, null, null, 12, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(100, "CARD", "CREDIT", card, null, 13, 12, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(100, "CARD", "CREDIT", card, null, 0, 12, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInstallmentOfAGroupCannotBeRepeated() {
        String group = "11111111-1111-1111-1111-111111111111";
        insert(100, "CARD", "CREDIT", card, null, 1, 3, group);
        insert(100, "CARD", "CREDIT", card, null, 2, 3, group);
        assertThatThrownBy(() -> insert(100, "CARD", "CREDIT", card, null, 2, 3, group))
                .isInstanceOf(DataIntegrityViolationException.class);
        // expenses without a group never collide
        insert(100, "CARD", "CREDIT", card, null, 2, 3, null);
        insert(100, "CARD", "CREDIT", card, null, 2, 3, null);
    }

    @Test
    void referencesMustExist() {
        assertThatThrownBy(() -> insert(100, "CARD", "CREDIT", 999_999L, null, null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(100, "CARD", "CREDIT", card, 999_999L, null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        insert(100, "CARD", "CREDIT", card, person, null, null, null);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO expense (user_id, month_ref, name, category_id, amount_cents, type, payment_method, expense_date)
                VALUES (?, '2026-11', 'x', 999999, 100, 'FIXED', 'PIX', DATE '2026-11-05')""", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
