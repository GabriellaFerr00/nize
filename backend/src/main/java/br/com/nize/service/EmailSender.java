package br.com.nize.service;

/** Outgoing email port. Dev uses {@link LoggingEmailSender}; a real SMTP sender can replace it later. */
public interface EmailSender {

    void sendPasswordReset(String toEmail, String token);
}
