-- NFR-001 / NFR-005 / NFR-009: every row returned here is a violation; the pipeline fails on any output.
-- Money columns must be BIGINT cents.
SELECT CONCAT('money column not BIGINT: ', table_name, '.', column_name, ' ', data_type)
FROM information_schema.columns
WHERE table_schema = 'nize' AND table_name <> 'flyway_schema_history'
  AND column_name REGEXP 'amount|cents|valor|total|balance|saldo|price|preco'
  AND data_type <> 'bigint'
UNION ALL
-- Passwords exist only as a hash.
SELECT CONCAT('password column without hash: ', table_name, '.', column_name)
FROM information_schema.columns
WHERE table_schema = 'nize' AND column_name REGEXP 'pass|senha' AND column_name <> 'password_hash'
UNION ALL
-- Tokens exist only as a hash.
SELECT CONCAT('token column without hash: ', table_name, '.', column_name)
FROM information_schema.columns
WHERE table_schema = 'nize' AND column_name REGEXP 'token' AND column_name <> 'token_hash'
UNION ALL
-- Personal data allowed by policy: e-mail (and name) only. No CPF, card numbers or bank data.
SELECT CONCAT('personal data not allowed by policy: ', table_name, '.', column_name)
FROM information_schema.columns
WHERE table_schema = 'nize'
  AND column_name REGEXP 'cpf|cnpj|card_number|cardnumber|pan|cvv|cvc|iban|agencia|account_number|conta';
