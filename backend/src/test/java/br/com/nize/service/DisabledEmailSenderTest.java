package br.com.nize.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** D-17 / NFR-008: the prod sender never writes the address or the token. */
@ExtendWith(OutputCaptureExtension.class)
class DisabledEmailSenderTest {

    @Test
    void logsNeitherAddressNorToken(CapturedOutput output) {
        new DisabledEmailSender().sendPasswordReset("someone@example.com", "secret-reset-token-42");

        assertThat(output.getAll()).contains("email delivery is not configured")
                .doesNotContain("someone@example.com", "secret-reset-token-42");
    }
}
