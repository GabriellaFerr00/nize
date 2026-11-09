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

import java.time.LocalDate;

/** One income of one month (REQ-024..026); with a person it is a reimbursement. Money is cents (NFR-001). */
@Entity
@Table(name = "income")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "month_ref", nullable = false, length = 7)
    private String month;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "income_date", nullable = false)
    private LocalDate date;

    // DECIMAL(12,2) in reais in the database, cents here (D-2)
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    @Convert(converter = MoneyColumnConverter.class)
    private long amountCents;

    @Column(name = "person_id")
    private Long personId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "installment_number")
    private Integer installmentNumber;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "installment_count")
    private Integer installmentCount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "installment_group_id", length = 36)
    private String installmentGroupId;

    protected Income() {
    }

    public Income(Long userId, String month, String name, LocalDate date, long amountCents, Long personId,
                  Integer installmentNumber, Integer installmentCount, String installmentGroupId) {
        this.userId = userId;
        this.month = month;
        this.name = name;
        this.date = date;
        this.amountCents = amountCents;
        this.personId = personId;
        this.installmentNumber = installmentNumber;
        this.installmentCount = installmentCount;
        this.installmentGroupId = installmentGroupId;
    }

    /** Edits what the user may change; month and installment data stay as they were. */
    public void update(String name, LocalDate date, long amountCents, Long personId) {
        this.name = name;
        this.date = date;
        this.amountCents = amountCents;
        this.personId = personId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getMonth() { return month; }
    public String getName() { return name; }
    public LocalDate getDate() { return date; }
    public long getAmountCents() { return amountCents; }
    public Long getPersonId() { return personId; }
    public Integer getInstallmentNumber() { return installmentNumber; }
    public Integer getInstallmentCount() { return installmentCount; }
    public String getInstallmentGroupId() { return installmentGroupId; }
}
