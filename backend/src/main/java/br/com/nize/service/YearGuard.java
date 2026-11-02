package br.com.nize.service;

import br.com.nize.domain.UserYearId;
import br.com.nize.repository.UserYearRepository;
import org.springframework.stereotype.Component;

/** Per-year resources only exist inside a year the user has; any other year is "not found" (D-6). */
@Component
public class YearGuard {

    private final UserYearRepository years;

    public YearGuard(UserYearRepository years) {
        this.years = years;
    }

    public void requireYear(Long userId, int year) {
        if (!years.existsById(new UserYearId(userId, year))) {
            throw new ResourceNotFoundException();
        }
    }
}
