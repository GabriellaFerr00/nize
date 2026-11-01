package br.com.nize.service;

import br.com.nize.domain.AppUser;
import br.com.nize.repository.AppUserRepository;
import br.com.nize.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    /** Compared against when the email is unknown, so both failures cost the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("nize-dummy-password");
    }

    /** Same exception for unknown email and wrong password (REQ-002). */
    public TokenService.IssuedToken login(String email, String rawPassword) {
        AppUser user = users.findByEmail(RegistrationService.normalizeEmail(email)).orElse(null);
        boolean matches = passwordEncoder.matches(rawPassword, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokens.issueFor(user.getId());
    }
}
