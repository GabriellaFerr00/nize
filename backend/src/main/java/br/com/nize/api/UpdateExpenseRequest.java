package br.com.nize.api;

import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Full replacement of the editable fields (REQ-023); the month and the installment data cannot change. */
public record UpdateExpenseRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Long categoryId,
        @NotNull @Positive Long amountCents,
        @NotNull ExpenseType type,
        PaymentMethod paymentMethod,
        Long cardId,
        Long personId,
        @NotNull Boolean paid,
        @NotNull LocalDate date) {

    public UpdateExpenseRequest {
        name = name == null ? null : name.trim();
    }
}
