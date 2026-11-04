package br.com.nize.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Full replacement of the editable fields (REQ-023); the month and the installment data cannot change. */
public record UpdateIncomeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive Long amountCents,
        @NotNull LocalDate date,
        Long personId) {

    public UpdateIncomeRequest {
        name = name == null ? null : name.trim();
    }
}
