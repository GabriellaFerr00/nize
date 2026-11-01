package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    protected Settings() {
    }

    public Settings(Long userId, int currentYear, boolean autoInstallments) {
        this.userId = userId;
        this.currentYear = currentYear;
        this.autoInstallments = autoInstallments;
    }

    public void update(int currentYear, boolean autoInstallments) {
        this.currentYear = currentYear;
        this.autoInstallments = autoInstallments;
    }

    public Long getUserId() { return userId; }
    public int getCurrentYear() { return currentYear; }
    public boolean isAutoInstallments() { return autoInstallments; }
}
