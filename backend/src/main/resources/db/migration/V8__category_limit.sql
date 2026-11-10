-- V8: optional monthly spending limit per category (REQ-051), DECIMAL(12,2) in reais like every money column (D-2).
-- NULL = no limit. Compatible with MySQL 8.4 and H2 (MySQL mode).

ALTER TABLE category ADD COLUMN limit_amount DECIMAL(12,2) NULL;
ALTER TABLE category ADD CONSTRAINT chk_category_limit CHECK (limit_amount IS NULL OR limit_amount > 0);
