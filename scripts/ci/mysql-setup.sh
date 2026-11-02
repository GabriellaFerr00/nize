#!/usr/bin/env bash
# Creates the `nize` database and the two users of D-14 in the job's throwaway MySQL:
#   migration user: DDL + DML on nize.* only (used only by Flyway)
#   runtime user:   SELECT, INSERT, UPDATE, DELETE on nize.* only (used by the app)
# Inputs (env, never hardcoded): MYSQL_CONTAINER, MYSQL_ROOT_PASSWORD,
#   NIZE_DB_MIGRATION_USER, NIZE_DB_MIGRATION_PASSWORD, NIZE_DB_USER, NIZE_DB_PASSWORD.
set -euo pipefail

for v in MYSQL_CONTAINER MYSQL_ROOT_PASSWORD NIZE_DB_MIGRATION_USER NIZE_DB_MIGRATION_PASSWORD NIZE_DB_USER NIZE_DB_PASSWORD; do
  [[ -n "${!v:-}" ]] || { echo "::error::$v is not set"; exit 1; }
done
for v in NIZE_DB_MIGRATION_PASSWORD NIZE_DB_PASSWORD; do
  [[ "${!v}" =~ ^[A-Za-z0-9_-]{20,}$ ]] || { echo "::error::$v must be 20+ chars of [A-Za-z0-9_-]"; exit 1; }
done

docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS nize CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${NIZE_DB_MIGRATION_USER}'@'%' IDENTIFIED BY '${NIZE_DB_MIGRATION_PASSWORD}' REQUIRE SSL;
GRANT CREATE, ALTER, DROP, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE ON nize.* TO '${NIZE_DB_MIGRATION_USER}'@'%';
CREATE USER IF NOT EXISTS '${NIZE_DB_USER}'@'%' IDENTIFIED BY '${NIZE_DB_PASSWORD}' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE ON nize.* TO '${NIZE_DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL
echo "Database nize and users created (migration: DDL on nize.*; runtime: DML on nize.*)."
