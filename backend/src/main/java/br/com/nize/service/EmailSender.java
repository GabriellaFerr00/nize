package br.com.nize.service;

/** Outgoing email port. Dev uses {@link LoggingEmailSender}, prod {@link DisabledEmailSender} until a provider exists. */
public interface EmailSender {

    void sendPasswordReset(String toEmail, String token);
}
