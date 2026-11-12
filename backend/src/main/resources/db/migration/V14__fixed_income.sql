-- V14: fixed income (REQ-053, 2026-11-12). A fixed income is copied to the next month by the month load (REQ-022),
-- like a fixed expense. Never together with installments (checked by the API). Compatible with MySQL 8.4 and H2.

ALTER TABLE income ADD COLUMN fixed BOOLEAN NOT NULL DEFAULT FALSE;
