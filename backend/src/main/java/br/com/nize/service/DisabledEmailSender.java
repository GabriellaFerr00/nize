package br.com.nize.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production sender until an email provider is chosen (D-17): sends nothing and never logs
 * the address or the token (NFR-008).
 */
@Component
@Profile("prod")
class DisabledEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(DisabledEmailSender.class);

    @Override
    public void sendPasswordReset(String toEmail, String token) {
        log.warn("Password reset requested, but email delivery is not configured in this environment");
    }
}
