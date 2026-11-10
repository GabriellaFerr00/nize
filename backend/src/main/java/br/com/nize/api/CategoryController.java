package br.com.nize.api;

import br.com.nize.security.CurrentUser;
import br.com.nize.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    /** {@code ?active=true} returns only the categories offered in the entry form. */
    @GetMapping("/api/categories")
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean active) {
        return service.list(CurrentUser.id(), active).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return CategoryResponse.from(service.create(CurrentUser.id(), request.name(), request.description(),
                request.monthlyLimitCents(), request.color()));
    }

    @PutMapping("/api/categories/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryResponse.from(service.update(
                CurrentUser.id(), id, request.name(), request.description(), request.active(),
                request.monthlyLimitCents(), request.color()));
    }

    /** 204 when deleted; 409 {@code CATEGORY_IN_USE} with the expense count when expenses still use it. */
    @DeleteMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }
}
