package br.com.nize.api;

import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * No user id here on purpose; money is a whole number of cents (NFR-001). With {@code generateInstallments} the
 * purchase is entered once: {@code installmentCount} installments, and {@code amountCents} is the total
 * ({@code amountIsTotal} true, the default) or the value of each installment.
 */
public record CreateExpenseRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Long categoryId,
        @NotNull @Positive Long amountCents,
        @NotNull ExpenseType type,
        PaymentMethod paymentMethod,
        Long cardId,
        Long personId,
        Boolean paid,
        @NotNull LocalDate date,
        Integer installmentNumber,
        Integer installmentCount,
        Boolean generateInstallments,
        Boolean amountIsTotal) {

    public CreateExpenseRequest {
        name = name == null ? null : name.trim();
    }
}
