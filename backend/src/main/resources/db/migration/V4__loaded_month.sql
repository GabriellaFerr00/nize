-- V4: marks the months whose automatic load already ran (REQ-022, D-7). Compatible with MySQL and H2.

CREATE TABLE loaded_month (
    user_id   BIGINT    NOT NULL,
    month_ref CHAR(7)   NOT NULL,
    loaded_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, month_ref),
    CONSTRAINT fk_loaded_month_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
