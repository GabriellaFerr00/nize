# Getting Started

Run with the in-memory H2 database:

    ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

Run the tests:

    ./mvnw test

On Windows use `mvnw.cmd`. Java 25 is required (`JAVA_HOME`); Maven is downloaded by the wrapper.
Local MySQL profile: `-Dspring-boot.run.profiles=local` with `NIZE_DB_URL`, `NIZE_DB_USER`, `NIZE_DB_PASSWORD`.

## JWT secret

The API signs tokens with `NIZE_JWT_SECRET` (at least 32 bytes). The app refuses to start without it:

    export NIZE_JWT_SECRET='change-me-change-me-change-me-0123456789'

Tests set their own fixed secret through the Surefire configuration in `pom.xml`.

## Local MySQL (profile `local`)

The WSL reaches the Windows MySQL at the default gateway (`ip route | awk '/default/{print $3}'`). Create a
dedicated user (the WSL subnet may change after a Windows restart; repeat with the new range if so):

    CREATE DATABASE IF NOT EXISTS nize CHARACTER SET utf8mb4;
    CREATE USER 'nize'@'172.28.%' IDENTIFIED BY '<password>';
    GRANT ALL PRIVILEGES ON nize.* TO 'nize'@'172.28.%';

Then run with `NIZE_DB_URL` (jdbc:mysql://<gateway>:3306/nize), `NIZE_DB_USER`, `NIZE_DB_PASSWORD` and `NIZE_JWT_SECRET` set.
