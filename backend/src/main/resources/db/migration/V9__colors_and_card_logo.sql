-- V9: colors of categories and people, and the card logo (REQ-052, D-21). Compatible with MySQL 8.4 and H2.
-- A color is a key of the Nize palette; NULL = neutral. The logo is the user's own image, checked by its first bytes.

ALTER TABLE category ADD COLUMN color VARCHAR(16) NULL;
ALTER TABLE category ADD CONSTRAINT chk_category_color
    CHECK (color IS NULL OR color IN ('green', 'teal', 'blue', 'purple', 'pink', 'red', 'orange', 'brown'));

ALTER TABLE person ADD COLUMN color VARCHAR(16) NULL;
ALTER TABLE person ADD CONSTRAINT chk_person_color
    CHECK (color IS NULL OR color IN ('green', 'teal', 'blue', 'purple', 'pink', 'red', 'orange', 'brown'));

CREATE TABLE card_logo (
    card_id      BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    data         MEDIUMBLOB  NOT NULL,
    PRIMARY KEY (card_id),
    CONSTRAINT fk_card_logo_card FOREIGN KEY (card_id) REFERENCES card (id),
    CONSTRAINT fk_card_logo_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_card_logo_type CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp'))
);

CREATE INDEX ix_card_logo_user ON card_logo (user_id);
