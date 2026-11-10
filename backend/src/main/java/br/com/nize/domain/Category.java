package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An expense category of one user in one year (REQ-010, REQ-011). */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "year_number", nullable = false)
    private int yearNumber;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    // optional monthly limit (REQ-051): DECIMAL(12,2) in reais in the database, cents here (D-2); null = no limit
    @Column(name = "limit_amount", precision = 12, scale = 2)
    @Convert(converter = MoneyColumnConverter.class)
    private Long monthlyLimitCents;

    protected Category() {
    }

    public Category(Long userId, int yearNumber, String name, String description) {
        this.userId = userId;
        this.yearNumber = yearNumber;
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description, boolean active) {
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public void setMonthlyLimitCents(Long monthlyLimitCents) {
        this.monthlyLimitCents = monthlyLimitCents;
    }

    public Long getMonthlyLimitCents() { return monthlyLimitCents; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getYearNumber() { return yearNumber; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
