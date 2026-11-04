-- V5: incomes (REQ-024..026). Compatible with MySQL and H2 (MySQL mode).
-- amount_cents is BIGINT cents (NFR-001); no totals or balances are stored (NFR-002).
-- person_id set = reimbursement from a third party (REQ-026).

CREATE TABLE income (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    month_ref            CHAR(7)      NOT NULL,
    name                 VARCHAR(120) NOT NULL,
    income_date          DATE         NOT NULL,
    amount_cents         BIGINT       NOT NULL,
    person_id            BIGINT       NULL,
    installment_number   SMALLINT     NULL,
    installment_count    SMALLINT     NULL,
    installment_group_id CHAR(36)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_income_user   FOREIGN KEY (user_id)   REFERENCES app_user (id),
    CONSTRAINT fk_income_person FOREIGN KEY (person_id) REFERENCES person (id),
    CONSTRAINT chk_income_amount CHECK (amount_cents > 0),
    CONSTRAINT chk_income_installments CHECK (
        (installment_number IS NULL AND installment_count IS NULL)
        OR (installment_number IS NOT NULL AND installment_count IS NOT NULL
            AND installment_number >= 1 AND installment_number <= installment_count)),
    CONSTRAINT uq_income_installment UNIQUE (installment_group_id, installment_number)
);

CREATE INDEX ix_income_user_month ON income (user_id, month_ref);
