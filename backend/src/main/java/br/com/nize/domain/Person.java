package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A third party who uses the user's card (e.g. "Mainha"); not a login (REQ-014). */
@Entity
@Table(name = "person")
public class Person {

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

    @Column(nullable = false)
    private boolean active = true;

    // a key of the Nize palette (REQ-052, D-21); null = neutral
    @Column(length = 16)
    private String color;

    protected Person() {
    }

    public Person(Long userId, int yearNumber, String name) {
        this.userId = userId;
        this.yearNumber = yearNumber;
        this.name = name;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getColor() { return color; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getYearNumber() { return yearNumber; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
