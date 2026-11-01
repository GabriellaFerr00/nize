package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserYearId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "year_number")
    private int yearNumber;

    protected UserYearId() {
    }

    public UserYearId(Long userId, int yearNumber) {
        this.userId = userId;
        this.yearNumber = yearNumber;
    }

    public Long getUserId() { return userId; }
    public int getYearNumber() { return yearNumber; }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserYearId other
                && Objects.equals(userId, other.userId) && yearNumber == other.yearNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, yearNumber);
    }
}
