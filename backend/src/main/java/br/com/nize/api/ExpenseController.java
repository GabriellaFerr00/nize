package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.ExpenseService;
import jakarta.validation.Valid;
import br.com.nize.domain.Expense;
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping("/api/months/{month}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@PathVariable String month, @Valid @RequestBody CreateExpenseRequest r) {
        List<Expense> created = service.create(CurrentUser.id(), month, new ExpenseService.NewExpense(
                r.name(), r.categoryId(), r.amountCents(), r.type(), r.paymentMethod(), r.cardId(), r.personId(),
                r.paid(), r.date(), r.installmentNumber(), r.installmentCount(),
                Boolean.TRUE.equals(r.generateInstallments()), !Boolean.FALSE.equals(r.amountIsTotal())));
        // the response is the expense of the requested month; several were created when generating installments
        return ExpenseResponse.from(created.get(0), created.size() > 1 ? created.size() : null);
    }

    @PutMapping("/api/expenses/{id}")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody UpdateExpenseRequest r) {
        return ExpenseResponse.from(service.update(CurrentUser.id(), id, new ExpenseService.ExpenseEdit(
                r.name(), r.categoryId(), r.amountCents(), r.type(), r.paymentMethod(), r.cardId(), r.personId(),
                r.paid(), r.date())), null);
    }

    /** Only the "paid" control mark changes (REQ-033). */
    @PatchMapping("/api/expenses/{id}/paid")
    public ExpenseResponse paid(@PathVariable Long id, @Valid @RequestBody PaidRequest r) {
        return ExpenseResponse.from(service.setPaid(CurrentUser.id(), id, r.paid()), null);
    }

    @DeleteMapping("/api/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }
}
