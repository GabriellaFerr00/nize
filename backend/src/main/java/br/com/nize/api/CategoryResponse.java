package br.com.nize.api;

import br.com.nize.domain.Category;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryResponse(Long id, int year, String name, String description, boolean active,
                               Long monthlyLimitCents, String color) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getYearNumber(), category.getName(),
                category.getDescription(), category.isActive(), category.getMonthlyLimitCents(),
                category.getColor());
    }
}
