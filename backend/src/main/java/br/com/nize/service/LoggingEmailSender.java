package br.com.nize.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Development sender: writes the reset token to the log instead of sending an email. */
@Component
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendPasswordReset(String toEmail, String token) {
        log.info("[DEV EMAIL] password reset for {}: token={}", toEmail, token);
    }
}
