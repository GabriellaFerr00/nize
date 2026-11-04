#!/usr/bin/env bash
# NFR-009: the runtime user can read and write data but can never change the schema or users.
# Inputs (env): MYSQL_CONTAINER, NIZE_DB_USER, NIZE_DB_PASSWORD.
set -euo pipefail

run_as_app() {
  docker exec -i -e MYSQL_PWD="$NIZE_DB_PASSWORD" "$MYSQL_CONTAINER" \
    mysql --protocol=TCP -h 127.0.0.1 --ssl-mode=REQUIRED -u"$NIZE_DB_USER" -N nize -e "$1" 2>&1
}

if ! run_as_app "SELECT COUNT(*) FROM app_user" >/dev/null; then
  echo "::error::runtime user cannot read app_user"; exit 1
fi

failed=0
while IFS= read -r statement; do
  if out=$(run_as_app "$statement"); then
    echo "::error::runtime user was allowed to run: $statement"; failed=1
  elif ! grep -qiE "denied|ERROR 1044|ERROR 1045|ERROR 1142|ERROR 1227" <<<"$out"; then
    echo "::error::unexpected failure for '$statement': $out"; failed=1
  else
    echo "ok (denied): $statement"
  fi
done <<'SQL'
CREATE TABLE ci_should_fail (id INT)
ALTER TABLE app_user ADD COLUMN ci_should_fail INT
DROP TABLE app_user
TRUNCATE TABLE app_user
CREATE USER 'ci_should_fail'@'%' IDENTIFIED BY 'x'
GRANT SELECT ON nize.* TO 'ci_should_fail'@'%'
CREATE DATABASE ci_should_fail
SELECT COUNT(*) FROM mysql.user
SQL

grants=$(run_as_app "SHOW GRANTS")
if grep -qiE "SUPER|ALL PRIVILEGES|GRANT OPTION|CREATE|ALTER|DROP|FILE|PROCESS" <<<"$grants"; then
  echo "::error::runtime user has too many privileges:"; echo "$grants"; failed=1
fi
exit "$failed"
