package br.com.nize.service;

import br.com.nize.domain.UserYear;
import br.com.nize.domain.UserYearId;
import br.com.nize.repository.SettingsRepository;
import br.com.nize.repository.UserYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Years of a user (REQ-007, REQ-008): they only decide which months can be opened (D-22). */
@Service
public class YearService {

    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    public record YearInfo(int year, boolean current) {
    }

    private final UserYearRepository years;
    private final SettingsRepository settings;

    public YearService(UserYearRepository years, SettingsRepository settings) {
        this.years = years;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<YearInfo> list(Long userId) {
        int current = settings.findById(userId).orElseThrow().getCurrentYear();
        return years.findYearNumbers(userId).stream().map(y -> new YearInfo(y, y == current)).toList();
    }

    /** Creates the year, so its months can be opened (REQ-007). Catalogs have no year, so nothing is copied (D-22). */
    @Transactional
    public int create(Long userId, int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new InvalidYearException(year);
        }
        if (years.existsById(new UserYearId(userId, year))) {
            throw new YearAlreadyExistsException(year);
        }
        years.saveAndFlush(new UserYear(userId, year));
        return year;
    }
}
