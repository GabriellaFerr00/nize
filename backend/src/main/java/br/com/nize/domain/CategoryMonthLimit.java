package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

/** The limit of a category adjusted for one month, in place of its default limit (REQ-051, D-22). */
@Entity
@Table(name = "category_month_limit")
@IdClass(CategoryMonthLimit.Key.class)
public class CategoryMonthLimit {

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "month_ref", length = 7)
    private String month;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // DECIMAL(12,2) in reais in the database, cents here (D-2)
    @Column(name = "limit_amount", nullable = false, precision = 12, scale = 2)
    @Convert(converter = MoneyColumnConverter.class)
    private long limitCents;

    protected CategoryMonthLimit() {
    }

    public CategoryMonthLimit(Long categoryId, String month, Long userId, long limitCents) {
        this.categoryId = categoryId;
        this.month = month;
        this.userId = userId;
        this.limitCents = limitCents;
    }

    public void setLimitCents(long limitCents) {
        this.limitCents = limitCents;
    }

    public Long getCategoryId() { return categoryId; }
    public String getMonth() { return month; }
    public Long getUserId() { return userId; }
    public long getLimitCents() { return limitCents; }

    public static class Key implements Serializable {
        private Long categoryId;
        private String month;

        public Key() {
        }

        public Key(Long categoryId, String month) {
            this.categoryId = categoryId;
            this.month = month;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(categoryId, k.categoryId) && Objects.equals(month, k.month);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryId, month);
        }
    }
}
