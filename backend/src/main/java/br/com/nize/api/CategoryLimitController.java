package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.CategoryLimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static br.com.nize.domain.MoneyColumnConverter.MAX_CENTS;

/** The limits of one month: the default of each category and the month's own adjustment (REQ-051, D-22). */
@RestController
public class CategoryLimitController {

    public record MonthLimitRequest(@NotNull @Positive @Max(MAX_CENTS) Long limitCents) {
    }

    private final CategoryLimitService service;

    public CategoryLimitController(CategoryLimitService service) {
        this.service = service;
    }

    @GetMapping("/api/months/{month}/category-limits")
    public List<CategoryLimitService.MonthLimit> list(@PathVariable String month) {
        return service.list(CurrentUser.id(), month);
    }

    @PutMapping("/api/months/{month}/category-limits/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@PathVariable String month, @PathVariable Long categoryId, @Valid @RequestBody MonthLimitRequest r) {
        service.set(CurrentUser.id(), month, categoryId, r.limitCents());
    }

    /** Back to the category's default limit in that month. */
    @DeleteMapping("/api/months/{month}/category-limits/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String month, @PathVariable Long categoryId) {
        service.clear(CurrentUser.id(), month, categoryId);
    }
}
