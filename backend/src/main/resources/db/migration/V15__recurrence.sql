-- V15: a fixed expense or income and its copies in other months share a recurrence id (REQ-054, 2026-11-12), so a
-- fixed item reaches the months already opened, unchecking it takes it out of them, and a delete can take "this
-- month and the next ones". Compatible with MySQL 8.4 (sql_require_primary_key) and H2 (MySQL mode).

ALTER TABLE expense ADD COLUMN recurrence_id CHAR(36) NULL;
ALTER TABLE income ADD COLUMN recurrence_id CHAR(36) NULL;
CREATE INDEX idx_expense_recurrence ON expense (user_id, recurrence_id);
CREATE INDEX idx_income_recurrence ON income (user_id, recurrence_id);

-- Fixed items created before V15 were carried month to month without a link: link them by user and name.
CREATE TABLE tmp_recurrence (
    user_id  BIGINT       NOT NULL,
    name     VARCHAR(120) NOT NULL,
    first_id BIGINT       NOT NULL,
    PRIMARY KEY (user_id, name)
);

INSERT INTO tmp_recurrence (user_id, name, first_id)
SELECT user_id, name, MIN(id) FROM expense WHERE type = 'FIXED' GROUP BY user_id, name;

UPDATE expense SET recurrence_id = (
    SELECT CONCAT('legacy-', t.first_id) FROM tmp_recurrence t WHERE t.user_id = expense.user_id AND t.name = expense.name
) WHERE type = 'FIXED';

DELETE FROM tmp_recurrence;

INSERT INTO tmp_recurrence (user_id, name, first_id)
SELECT user_id, name, MIN(id) FROM income WHERE fixed = TRUE GROUP BY user_id, name;

UPDATE income SET recurrence_id = (
    SELECT CONCAT('legacy-i', t.first_id) FROM tmp_recurrence t WHERE t.user_id = income.user_id AND t.name = income.name
) WHERE fixed = TRUE;

DROP TABLE tmp_recurrence;
