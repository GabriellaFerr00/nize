package br.com.nize.api;

import static br.com.nize.domain.MoneyColumnConverter.MAX_CENTS;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Full replacement of the editable fields (REQ-023); the month and the installment data cannot change;
 * {@code fixed} absent = false (REQ-053). */
public record UpdateIncomeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive @Max(MAX_CENTS) Long amountCents,
        @NotNull LocalDate date,
        Long personId,
        Boolean fixed) {

    public UpdateIncomeRequest {
        name = name == null ? null : name.trim();
    }
}
