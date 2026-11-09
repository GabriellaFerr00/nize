package br.com.nize.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

/**
 * Money is stored as DECIMAL(12,2) in reais and used everywhere else as integer cents (D-2, NFR-001). This is the only
 * place where BigDecimal touches money: 1234 cents ↔ 12.34. Never rounds: a value with more than 2 decimal places or
 * outside a long fails instead of losing cents.
 */
@Converter
public class MoneyColumnConverter implements AttributeConverter<Long, BigDecimal> {

    /** Largest amount DECIMAL(12,2) holds: 9 999 999 999.99 reais. */
    public static final long MAX_CENTS = 999_999_999_999L;

    @Override
    public BigDecimal convertToDatabaseColumn(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }

    @Override
    public Long convertToEntityAttribute(BigDecimal reais) {
        return reais == null ? null : reais.movePointRight(2).longValueExact();
    }
}
