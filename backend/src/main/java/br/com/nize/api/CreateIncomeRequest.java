package br.com.nize.api;

import static br.com.nize.domain.MoneyColumnConverter.MAX_CENTS;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** No user id here on purpose; {@code fixed} absent = false (REQ-053); money is a whole number of cents (NFR-001). With a person it is a reimbursement. */
public record CreateIncomeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive @Max(MAX_CENTS) Long amountCents,
        @NotNull LocalDate date,
        Long personId,
        Integer installmentNumber,
        Integer installmentCount,
        Boolean fixed) {

    public CreateIncomeRequest {
        name = name == null ? null : name.trim();
    }
}
