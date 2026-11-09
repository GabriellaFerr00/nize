-- V7: money columns become DECIMAL(12,2) in reais (D-2, NFR-001, user request 2026-11-09).
-- The back-end keeps working in integer cents: MoneyColumnConverter converts on read and write.
-- Compatible with MySQL 8.4 and H2 (MySQL mode). amount_cents / 100 is exact for whole cents.

ALTER TABLE expense ADD COLUMN amount DECIMAL(12,2) NULL;
UPDATE expense SET amount = amount_cents / 100;
ALTER TABLE expense DROP CONSTRAINT chk_expense_amount;
ALTER TABLE expense DROP COLUMN amount_cents;
ALTER TABLE expense MODIFY COLUMN amount DECIMAL(12,2) NOT NULL;
ALTER TABLE expense ADD CONSTRAINT chk_expense_amount CHECK (amount > 0);

ALTER TABLE income ADD COLUMN amount DECIMAL(12,2) NULL;
UPDATE income SET amount = amount_cents / 100;
ALTER TABLE income DROP CONSTRAINT chk_income_amount;
ALTER TABLE income DROP COLUMN amount_cents;
ALTER TABLE income MODIFY COLUMN amount DECIMAL(12,2) NOT NULL;
ALTER TABLE income ADD CONSTRAINT chk_income_amount CHECK (amount > 0);
