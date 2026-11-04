package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.OverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OverviewController {

    private final OverviewService service;

    public OverviewController(OverviewService service) {
        this.service = service;
    }

    /** Expenses, incomes and balance of each month of the year; opens the months up to the current one (D-19). */
    @GetMapping("/api/years/{year}/overview")
    public OverviewResponse get(@PathVariable int year) {
        return new OverviewResponse(year, service.of(CurrentUser.id(), year));
    }
}
