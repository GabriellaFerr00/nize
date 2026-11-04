package br.com.nize.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "settings")
public class Settings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "current_year", nullable = false)
    private int currentYear;

    @Column(name = "auto_installments", nullable = false)
    private boolean autoInstallments;

    @Column(name = "closings_enabled", nullable = false)
    private boolean closingsEnabled = true;

    /** Days 1–31; a day past the end of the month counts as the last day (REQ-048). Eager: the API reads them after the transaction ends. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "closing_day", joinColumns = @JoinColumn(name = "user_id"))
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "day_number", nullable = false)
    private Set<Integer> closingDays = new HashSet<>();

    /** A new account starts with closings on the 15th and on the last day of the month. */
    public static final List<Integer> DEFAULT_CLOSING_DAYS = List.of(15, 31);

    protected Settings() {
    }

    public Settings(Long userId, int currentYear, boolean autoInstallments) {
        this.userId = userId;
        this.currentYear = currentYear;
        this.autoInstallments = autoInstallments;
        this.closingDays.addAll(DEFAULT_CLOSING_DAYS);
    }

    public void updateClosings(boolean enabled, Collection<Integer> days) {
        this.closingsEnabled = enabled;
        this.closingDays.clear();
        this.closingDays.addAll(days);
    }

    public void update(int currentYear, boolean autoInstallments) {
        this.currentYear = currentYear;
        this.autoInstallments = autoInstallments;
    }

    public Long getUserId() { return userId; }
    public int getCurrentYear() { return currentYear; }
    public boolean isAutoInstallments() { return autoInstallments; }
    public boolean isClosingsEnabled() { return closingsEnabled; }

    /** In ascending order. */
    public List<Integer> getClosingDays() { return closingDays.stream().sorted().toList(); }
}
