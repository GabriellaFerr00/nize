-- V6: optional month closings (REQ-048). Compatible with MySQL and H2 (MySQL mode).
-- A day past the end of the month counts as the last day (31 = always the last day).

ALTER TABLE settings ADD COLUMN closings_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE closing_day (
    user_id    BIGINT   NOT NULL,
    day_number SMALLINT NOT NULL,
    PRIMARY KEY (user_id, day_number),
    CONSTRAINT fk_closing_day_settings FOREIGN KEY (user_id) REFERENCES settings (user_id),
    CONSTRAINT chk_closing_day_range CHECK (day_number BETWEEN 1 AND 31)
);

-- Existing accounts get the defaults of a new account: the 15th and the last day.
INSERT INTO closing_day (user_id, day_number) SELECT user_id, 15 FROM settings;
INSERT INTO closing_day (user_id, day_number) SELECT user_id, 31 FROM settings;
