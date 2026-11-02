package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LoadedMonthId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "month_ref", length = 7)
    private String month;

    protected LoadedMonthId() {
    }

    public LoadedMonthId(Long userId, String month) {
        this.userId = userId;
        this.month = month;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LoadedMonthId other && Objects.equals(userId, other.userId) && Objects.equals(month, other.month);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, month);
    }
}
