package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.MonthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonthController {

    private final MonthService service;

    public MonthController(MonthService service) {
        this.service = service;
    }

    /** Opens the month (load on first open) and returns lists, totals, positions and own balance (REQ-041). */
    @GetMapping("/api/months/{month}")
    public MonthResponse get(@PathVariable String month) {
        return MonthResponse.from(service.open(CurrentUser.id(), month));
    }
}
