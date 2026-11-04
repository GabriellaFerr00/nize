package br.com.nize.api;

import br.com.nize.domain.Income;
import br.com.nize.rules.InstallmentIndicator;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncomeResponse(Long id, String month, String name, LocalDate date, long amountCents, Long personId,
                             boolean reimbursement, Integer installmentNumber, Integer installmentCount,
                             String installmentLabel) {

    static IncomeResponse from(Income i) {
        String label = InstallmentIndicator.format(i.getInstallmentNumber(), i.getInstallmentCount());
        return new IncomeResponse(i.getId(), i.getMonth(), i.getName(), i.getDate(), i.getAmountCents(),
                i.getPersonId(), i.getPersonId() != null, i.getInstallmentNumber(), i.getInstallmentCount(),
                label.isEmpty() ? null : label);
    }
}
