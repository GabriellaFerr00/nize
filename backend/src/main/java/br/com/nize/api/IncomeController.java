package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.DeleteScope;
import br.com.nize.service.IncomeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncomeController {

    private final IncomeService service;

    public IncomeController(IncomeService service) {
        this.service = service;
    }

    @PostMapping("/api/months/{month}/incomes")
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse create(@PathVariable String month, @Valid @RequestBody CreateIncomeRequest r) {
        return IncomeResponse.from(service.create(CurrentUser.id(), month, new IncomeService.NewIncome(
                r.name(), r.amountCents(), r.date(), r.personId(), r.installmentNumber(), r.installmentCount(),
                Boolean.TRUE.equals(r.fixed()))));
    }

    @PutMapping("/api/incomes/{id}")
    public IncomeResponse update(@PathVariable Long id, @Valid @RequestBody UpdateIncomeRequest r) {
        return IncomeResponse.from(service.update(CurrentUser.id(), id,
                new IncomeService.IncomeEdit(r.name(), r.amountCents(), r.date(), r.personId(),
                        Boolean.TRUE.equals(r.fixed()))));
    }

    /** {@code ?scope=following} also deletes the next installments of the same purchase (REQ-050); default "this". */
    @DeleteMapping("/api/incomes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam(required = false) String scope) {
        service.delete(CurrentUser.id(), id, DeleteScope.parse(scope));
    }
}
