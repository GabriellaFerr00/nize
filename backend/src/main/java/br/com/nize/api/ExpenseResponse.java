package br.com.nize.api;

import br.com.nize.domain.Expense;
import br.com.nize.domain.ExpenseType;
import br.com.nize.domain.PaymentMethod;
import br.com.nize.rules.InstallmentIndicator;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseResponse(Long id, String month, String name, Long categoryId, long amountCents,
                              ExpenseType type, PaymentMethod paymentMethod, Long cardId, Long personId,
                              boolean paid, LocalDate date, Integer installmentNumber, Integer installmentCount,
                              String installmentLabel, Integer createdCount) {

    /** {@code createdCount} is only set when several installments were generated at once. */
    static ExpenseResponse from(Expense e, Integer createdCount) {
        String label = InstallmentIndicator.format(e.getInstallmentNumber(), e.getInstallmentCount());
        return new ExpenseResponse(e.getId(), e.getMonth(), e.getName(), e.getCategoryId(), e.getAmountCents(),
                e.getType(), e.getPaymentMethod(), e.getCardId(), e.getPersonId(), e.isPaid(), e.getDate(),
                e.getInstallmentNumber(), e.getInstallmentCount(), label.isEmpty() ? null : label, createdCount);
    }
}
