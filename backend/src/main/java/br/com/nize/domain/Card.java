package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A credit card of one user in one year; the due day decides which fortnight its expenses weigh in (REQ-013). */
@Entity
@Table(name = "card")
public class Card {

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

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(nullable = false)
    private boolean active = true;

    protected Card() {
    }

    public Card(Long userId, int yearNumber, String name, int dueDay) {
        this.userId = userId;
        this.yearNumber = yearNumber;
        this.name = name;
        this.dueDay = dueDay;
    }

    public void update(String name, int dueDay, boolean active) {
        this.name = name;
        this.dueDay = dueDay;
        this.active = active;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getYearNumber() { return yearNumber; }
    public String getName() { return name; }
    public int getDueDay() { return dueDay; }
    public boolean isActive() { return active; }
}
