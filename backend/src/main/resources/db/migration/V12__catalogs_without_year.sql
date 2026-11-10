-- V12: categories, cards and people no longer belong to a year (D-22, REQ-007, REQ-010..014; user decision
-- 2026-11-09). For each user and name (ignoring case) the row of the most recent year stays, with its fields;
-- expenses and incomes are pointed at it and the other copies are deleted. Then year_number goes away.
-- Also: category_month_limit, the limit of a category adjusted for one month (REQ-051).
-- Compatible with MySQL 8.4 (sql_require_primary_key on: every new table has a primary key) and H2 (MySQL mode).

CREATE TABLE category_merge (old_id BIGINT NOT NULL, keep_id BIGINT NOT NULL, PRIMARY KEY (old_id));
INSERT INTO category_merge (old_id, keep_id)
SELECT c.id, (SELECT c2.id FROM category c2
              WHERE c2.user_id = c.user_id AND LOWER(c2.name) = LOWER(c.name)
              ORDER BY c2.year_number DESC, c2.id DESC LIMIT 1)
FROM category c;

CREATE TABLE card_merge (old_id BIGINT NOT NULL, keep_id BIGINT NOT NULL, PRIMARY KEY (old_id));
INSERT INTO card_merge (old_id, keep_id)
SELECT c.id, (SELECT c2.id FROM card c2
              WHERE c2.user_id = c.user_id AND LOWER(c2.name) = LOWER(c.name)
              ORDER BY c2.year_number DESC, c2.id DESC LIMIT 1)
FROM card c;

CREATE TABLE person_merge (old_id BIGINT NOT NULL, keep_id BIGINT NOT NULL, PRIMARY KEY (old_id));
INSERT INTO person_merge (old_id, keep_id)
SELECT p.id, (SELECT p2.id FROM person p2
              WHERE p2.user_id = p.user_id AND LOWER(p2.name) = LOWER(p.name)
              ORDER BY p2.year_number DESC, p2.id DESC LIMIT 1)
FROM person p;

UPDATE expense SET category_id = (SELECT m.keep_id FROM category_merge m WHERE m.old_id = expense.category_id);
UPDATE expense SET card_id = (SELECT m.keep_id FROM card_merge m WHERE m.old_id = expense.card_id)
WHERE card_id IS NOT NULL;
UPDATE expense SET person_id = (SELECT m.keep_id FROM person_merge m WHERE m.old_id = expense.person_id)
WHERE person_id IS NOT NULL;
UPDATE income SET person_id = (SELECT m.keep_id FROM person_merge m WHERE m.old_id = income.person_id)
WHERE person_id IS NOT NULL;

DELETE FROM category WHERE id IN (SELECT old_id FROM category_merge WHERE old_id <> keep_id);
DELETE FROM card WHERE id IN (SELECT old_id FROM card_merge WHERE old_id <> keep_id);
DELETE FROM person WHERE id IN (SELECT old_id FROM person_merge WHERE old_id <> keep_id);

DROP TABLE category_merge;
DROP TABLE card_merge;
DROP TABLE person_merge;

-- the per-year unique keys also served the user_id foreign keys: index user_id first, then drop them.
-- Names stay unique per user ignoring case in the service (409 *_NAME_TAKEN).
CREATE INDEX ix_category_user ON category (user_id);
CREATE INDEX ix_card_user ON card (user_id);
CREATE INDEX ix_person_user ON person (user_id);
ALTER TABLE category DROP CONSTRAINT uq_category_user_year_name;
ALTER TABLE card DROP CONSTRAINT uq_card_user_year_name;
ALTER TABLE person DROP CONSTRAINT uq_person_user_year_name;
ALTER TABLE category DROP COLUMN year_number;
ALTER TABLE card DROP COLUMN year_number;
ALTER TABLE person DROP COLUMN year_number;

CREATE TABLE category_month_limit (
    category_id  BIGINT        NOT NULL,
    month_ref    CHAR(7)       NOT NULL,
    user_id      BIGINT        NOT NULL,
    limit_amount DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (category_id, month_ref),
    CONSTRAINT fk_category_month_limit_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_category_month_limit_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_category_month_limit CHECK (limit_amount > 0)
);

CREATE INDEX ix_category_month_limit_user ON category_month_limit (user_id, month_ref);
