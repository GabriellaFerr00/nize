package br.com.nize.security;

import br.com.nize.NizeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The app must not start without NIZE_JWT_SECRET of at least 32 bytes (D-5). */
class JwtSecretBootTest {

    private static void boot(String secret) {
        new SpringApplicationBuilder(NizeApplication.class)
                .profiles("h2")
                // command-line arguments outrank the NIZE_JWT_SECRET set for the test JVM
                .run("--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:bootcheck;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                        "--nize.jwt.secret=" + secret)
                .close();
    }

    @Test
    void shortOrMissingSecretStopsTheBoot() {
        for (String secret : new String[]{"", "only-31-bytes-long-secret-xxxxx"}) {
            assertThatThrownBy(() -> boot(secret))
                    .hasStackTraceContaining("NIZE_JWT_SECRET must be set to a secret of at least 32 bytes");
        }
    }

    @Test
    void secretOfThirtyTwoBytesBoots() {
        assertThat("exactly-32-bytes-long-secret-xyz").hasSize(32);
        boot("exactly-32-bytes-long-secret-xyz");
    }
}
