package br.com.nize.service;

import br.com.nize.domain.Settings;
import br.com.nize.domain.UserYearId;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.repository.UserYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every method takes the user id from the caller's token, never from the request (D-6). */
@Service
public class SettingsService {

    private final SettingsRepository settings;
    private final UserYearRepository years;

    public SettingsService(SettingsRepository settings, UserYearRepository years) {
        this.settings = settings;
        this.years = years;
    }

    @Transactional(readOnly = true)
    public Settings get(Long userId) {
        return settings.findById(userId).orElseThrow();
    }

    /** The current year must be one of the user's own years (REQ-006, REQ-007). */
    @Transactional
    public Settings update(Long userId, int currentYear, boolean autoInstallments) {
        if (!years.existsById(new UserYearId(userId, currentYear))) {
            throw new UnknownYearException(currentYear);
        }
        Settings current = settings.findById(userId).orElseThrow();
        current.update(currentYear, autoInstallments);
        return current;
    }
}
