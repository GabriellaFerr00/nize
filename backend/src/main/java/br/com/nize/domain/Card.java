package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A credit card of one user, the same in every month (D-22); the due day decides which fortnight its expenses weigh in (REQ-013). */
@Entity
@Table(name = "card")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;


    @Column(nullable = false, length = 80)
    private String name;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(nullable = false)
    private boolean active = true;

    // a key of CardBrand (REQ-052, D-21); null = no brand
    @Column(length = 32)
    private String brand;

    protected Card() {
    }

    public Card(Long userId, String name, int dueDay) {
        this.userId = userId;
        this.name = name;
        this.dueDay = dueDay;
    }

    public void update(String name, int dueDay, boolean active) {
        this.name = name;
        this.dueDay = dueDay;
        this.active = active;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getBrand() { return brand; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public int getDueDay() { return dueDay; }
    public boolean isActive() { return active; }
}
