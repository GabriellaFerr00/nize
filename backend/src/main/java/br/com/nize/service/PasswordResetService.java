package br.com.nize.service;

import br.com.nize.domain.AppUser;
import br.com.nize.domain.PasswordResetToken;
import br.com.nize.repository.AppUserRepository;
import br.com.nize.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final Duration expiration;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(AppUserRepository users, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder, EmailSender emailSender,
                                @Value("${nize.password-reset.expiration:30m}") Duration expiration) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.expiration = expiration;
    }

    /** Silent for unknown emails, so the response never reveals whether an account exists. */
    @Transactional
    public void requestReset(String email) {
        users.findByEmail(RegistrationService.normalizeEmail(email)).ifPresent(user -> {
            Instant now = Instant.now();
            tokens.invalidateOpenTokens(user.getId(), now);
            String rawToken = newToken();
            tokens.save(new PasswordResetToken(user.getId(), hash(rawToken), now.plus(expiration)));
            emailSender.sendPasswordReset(user.getEmail(), rawToken);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = tokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(InvalidResetTokenException::new);
        AppUser user = users.findById(token.getUserId()).orElseThrow(InvalidResetTokenException::new);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        tokens.invalidateOpenTokens(user.getId(), now); // any other open token of this user
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
