package br.com.nize.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A year that exists for a user (REQ-007). */
@Entity
@Table(name = "user_year")
public class UserYear {

    @EmbeddedId
    private UserYearId id;

    protected UserYear() {
    }

    public UserYear(Long userId, int yearNumber) {
        this.id = new UserYearId(userId, yearNumber);
    }

    public UserYearId getId() { return id; }
}
