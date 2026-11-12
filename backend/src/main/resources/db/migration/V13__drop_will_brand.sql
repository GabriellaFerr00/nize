-- V13: the Will Bank brand leaves the card list (REQ-052, D-21, 2026-11-12). Cards that used it keep their name and
-- lose only the brand (NULL = no brand), so a later edit passes the API check of CardBrand.

UPDATE card SET brand = NULL WHERE brand = 'will';
