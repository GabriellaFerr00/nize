-- V3: expenses (REQ-015..021). Compatible with MySQL and H2 (MySQL mode).
-- amount_cents is BIGINT cents (NFR-001); no totals or balances are stored (NFR-002).
-- type FIXED/CARD and payment_method PIX/CREDIT are independent, except that a CARD expense is always CREDIT.

CREATE TABLE expense (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    month_ref            CHAR(7)      NOT NULL,
    name                 VARCHAR(120) NOT NULL,
    category_id          BIGINT       NOT NULL,
    amount_cents         BIGINT       NOT NULL,
    type                 VARCHAR(10)  NOT NULL,
    payment_method       VARCHAR(10)  NOT NULL,
    card_id              BIGINT       NULL,
    person_id            BIGINT       NULL,
    paid                 BOOLEAN      NOT NULL DEFAULT FALSE,
    expense_date         DATE         NOT NULL,
    installment_number   SMALLINT     NULL,
    installment_count    SMALLINT     NULL,
    installment_group_id CHAR(36)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_expense_user     FOREIGN KEY (user_id)     REFERENCES app_user (id),
    CONSTRAINT fk_expense_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_expense_card     FOREIGN KEY (card_id)     REFERENCES card (id),
    CONSTRAINT fk_expense_person   FOREIGN KEY (person_id)   REFERENCES person (id),
    CONSTRAINT chk_expense_amount  CHECK (amount_cents > 0),
    CONSTRAINT chk_expense_type    CHECK (type IN ('FIXED', 'CARD')),
    CONSTRAINT chk_expense_payment CHECK (payment_method IN ('PIX', 'CREDIT')),
    CONSTRAINT chk_expense_card_is_credit CHECK (type <> 'CARD' OR payment_method = 'CREDIT'),
    CONSTRAINT chk_expense_card_needs_credit CHECK (card_id IS NULL OR payment_method = 'CREDIT'),
    CONSTRAINT chk_expense_installments CHECK (
        (installment_number IS NULL AND installment_count IS NULL)
        OR (installment_number IS NOT NULL AND installment_count IS NOT NULL
            AND installment_number >= 1 AND installment_number <= installment_count)),
    CONSTRAINT uq_expense_installment UNIQUE (installment_group_id, installment_number)
);

CREATE INDEX ix_expense_user_month ON expense (user_id, month_ref);
