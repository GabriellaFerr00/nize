package br.com.nize.service;

import br.com.nize.domain.AppUser;
import br.com.nize.domain.Settings;
import br.com.nize.domain.UserYear;
import br.com.nize.repository.AppUserRepository;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.repository.UserYearRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.Locale;

@Service
public class RegistrationService {

    private final AppUserRepository users;
    private final SettingsRepository settings;
    private final UserYearRepository years;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository users, SettingsRepository settings,
                               UserYearRepository years, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.settings = settings;
        this.years = years;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates the user, its settings and the current year (REQ-001). */
    @Transactional
    public AppUser register(String name, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        AppUser user;
        try {
            user = users.saveAndFlush(new AppUser(
                    name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword), Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException(); // concurrent registration with the same email
        }
        int currentYear = Year.now().getValue();
        settings.save(new Settings(user.getId(), currentYear, true));
        years.save(new UserYear(user.getId(), currentYear));
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
