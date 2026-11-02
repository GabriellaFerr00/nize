package br.com.nize.service;

import br.com.nize.domain.Category;
import br.com.nize.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategorySeeder {

    private final CategoryRepository categories;

    public CategorySeeder(CategoryRepository categories) {
        this.categories = categories;
    }

    /** Creates the factory categories for a user and year; does nothing if the year already has any. */
    @Transactional
    public void seedFactoryCategories(Long userId, int year) {
        if (categories.existsByUserIdAndYearNumber(userId, year)) {
            return;
        }
        categories.saveAll(DefaultCategories.ALL.stream()
                .map(entry -> new Category(userId, year, entry.name(), entry.description()))
                .toList());
    }
}
