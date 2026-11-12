package br.com.nize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** One expense of one month (REQ-015..021). Money is a whole number of cents (NFR-001). */
@Entity
@Table(name = "expense")
public class Expense {

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

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    // DECIMAL(12,2) in reais in the database, cents here (D-2)
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    @Convert(converter = MoneyColumnConverter.class)
    private long amountCents;

    // varchar, not a native enum column: keeps the MySQL schema validation happy
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ExpenseType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "person_id")
    private Long personId;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "expense_date", nullable = false)
    private LocalDate date;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "installment_number")
    private Integer installmentNumber;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "installment_count")
    private Integer installmentCount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "installment_group_id", length = 36)
    private String installmentGroupId;

    // shared by a fixed item and its copies in other months (REQ-054, V15)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "recurrence_id", length = 36)
    private String recurrenceId;

    protected Expense() {
    }

    public Expense(Long userId, String month, String name, Long categoryId, long amountCents, ExpenseType type,
                   PaymentMethod paymentMethod, Long cardId, Long personId, boolean paid, LocalDate date,
                   Integer installmentNumber, Integer installmentCount, String installmentGroupId) {
        this.userId = userId;
        this.month = month;
        this.name = name;
        this.categoryId = categoryId;
        this.amountCents = amountCents;
        this.type = type;
        this.paymentMethod = paymentMethod;
        this.cardId = cardId;
        this.personId = personId;
        this.paid = paid;
        this.date = date;
        this.installmentNumber = installmentNumber;
        this.installmentCount = installmentCount;
        this.installmentGroupId = installmentGroupId;
    }

    /** Edits what the user may change; month and installment data stay as they were. */
    public void update(String name, Long categoryId, long amountCents, ExpenseType type, PaymentMethod paymentMethod,
                       Long cardId, Long personId, boolean paid, LocalDate date) {
        this.name = name;
        this.categoryId = categoryId;
        this.amountCents = amountCents;
        this.type = type;
        this.paymentMethod = paymentMethod;
        this.cardId = cardId;
        this.personId = personId;
        this.paid = paid;
        this.date = date;
    }

    /** "Paid" is only a control mark: it never touches values or dates (REQ-033). */
    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public String getRecurrenceId() { return recurrenceId; }

    /** The recurrence of this fixed item, created on first use (REQ-054). */
    public String ensureRecurrence() {
        if (recurrenceId == null) {
            recurrenceId = java.util.UUID.randomUUID().toString();
        }
        return recurrenceId;
    }

    /** Makes this row a copy of a fixed item of another month (REQ-054). */
    public void joinRecurrence(String recurrenceId) {
        this.recurrenceId = recurrenceId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getMonth() { return month; }
    public String getName() { return name; }
    public Long getCategoryId() { return categoryId; }
    public long getAmountCents() { return amountCents; }
    public ExpenseType getType() { return type; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public Long getCardId() { return cardId; }
    public Long getPersonId() { return personId; }
    public boolean isPaid() { return paid; }
    public LocalDate getDate() { return date; }
    public Integer getInstallmentNumber() { return installmentNumber; }
    public Integer getInstallmentCount() { return installmentCount; }
    public String getInstallmentGroupId() { return installmentGroupId; }
}
