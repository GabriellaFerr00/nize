-- V10: card brand (REQ-052, D-21) and free colors. Compatible with MySQL 8.4 and H2 (MySQL mode).
-- brand: a key of CardBrand (checked by the API; the list grows without a migration). NULL = no brand.
-- color: one of the 8 quick keys or #rrggbb, checked by the API, so the palette CHECK of V9 goes away.

ALTER TABLE card ADD COLUMN brand VARCHAR(32) NULL;

ALTER TABLE category DROP CONSTRAINT chk_category_color;
ALTER TABLE person DROP CONSTRAINT chk_person_color;
