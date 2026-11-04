package br.com.nize.security;

import br.com.nize.NizeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NFR-009: in prod the MySQL URL must demand TLS. */
class DatabaseTlsGuardTest {

    @Test
    void acceptsRequiredAndVerifyModes() {
        for (String url : new String[]{
                "jdbc:mysql://db.example:12345/nize?sslMode=REQUIRED",
                "jdbc:mysql://db.example:12345/nize?useUnicode=true&sslmode=verify_ca",
                "jdbc:mysql://db.example:12345/nize?sslMode=VERIFY_IDENTITY&x=1"}) {
            assertThatCode(() -> DatabaseTlsGuard.requireTls(url)).as(url).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsMissingOrWeakModesWithoutEchoingTheUrl() {
        for (String url : new String[]{
                null, "", "jdbc:mysql://db.example:12345/nize",
                "jdbc:mysql://db.example:12345/nize?sslMode=PREFERRED",
                "jdbc:mysql://db.example:12345/nize?sslMode=DISABLED",
                "jdbc:mysql://db.example:12345/nize?notsslMode=REQUIRED"}) {
            assertThatThrownBy(() -> DatabaseTlsGuard.requireTls(url)).as(String.valueOf(url))
                    .isInstanceOf(IllegalStateException.class)
                    .message().doesNotContain("db.example");
        }
    }

    @Test
    void prodProfileStopsBeforeConnectingWhenTheUrlHasNoTls() {
        // Port 1 has no database: reaching the guard's message proves it ran before any connection.
        assertThatThrownBy(() -> new SpringApplicationBuilder(NizeApplication.class).profiles("prod")
                .run("--server.port=0", "--spring.datasource.url=jdbc:mysql://127.0.0.1:1/nize",
                        "--spring.datasource.username=x", "--spring.datasource.password=y").close())
                .hasStackTraceContaining("NIZE_DB_URL must set sslMode")
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(e).hasNoCause());
    }
}
