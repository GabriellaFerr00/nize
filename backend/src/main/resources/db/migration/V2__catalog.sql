-- V2: per-year catalogs of categories, cards and people (REQ-010, REQ-011, REQ-013, REQ-014).
-- Compatible with MySQL and H2 (MySQL mode). The unique key starts with user_id, so it also serves
-- as the per-user index.

CREATE TABLE category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    year_number SMALLINT     NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_category_user_year_name UNIQUE (user_id, year_number, name),
    CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE card (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    year_number SMALLINT    NOT NULL,
    name        VARCHAR(80) NOT NULL,
    due_day     TINYINT     NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_card_user_year_name UNIQUE (user_id, year_number, name),
    CONSTRAINT chk_card_due_day CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT fk_card_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE person (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    year_number SMALLINT    NOT NULL,
    name        VARCHAR(80) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_person_user_year_name UNIQUE (user_id, year_number, name),
    CONSTRAINT fk_person_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
