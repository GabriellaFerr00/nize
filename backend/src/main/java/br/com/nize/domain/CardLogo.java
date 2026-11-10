package br.com.nize.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The user's own image for one card (REQ-052, D-21); at most one per card. */
@Entity
@Table(name = "card_logo")
public class CardLogo {

    @Id
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 16)
    private String contentType;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.LONG32VARBINARY)
    @Column(name = "data", nullable = false, columnDefinition = "mediumblob")
    private byte[] data;

    protected CardLogo() {
    }

    public CardLogo(Long cardId, Long userId, String contentType, byte[] data) {
        this.cardId = cardId;
        this.userId = userId;
        this.contentType = contentType;
        this.data = data;
    }

    public void replace(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
    }

    public Long getCardId() { return cardId; }
    public Long getUserId() { return userId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
