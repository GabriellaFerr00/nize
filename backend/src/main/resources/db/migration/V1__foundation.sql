-- V1: foundation (REQ-001, REQ-003, REQ-006, REQ-007, REQ-009). Compatible with MySQL and H2 (MySQL mode).

CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    onboarding_seen BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE settings (
    user_id           BIGINT   NOT NULL,
    current_year      SMALLINT NOT NULL,
    auto_installments BOOLEAN  NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE user_year (
    user_id     BIGINT   NOT NULL,
    year_number SMALLINT NOT NULL,
    PRIMARY KEY (user_id, year_number),
    CONSTRAINT fk_user_year_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE password_reset_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_password_reset_token_user ON password_reset_token (user_id);
