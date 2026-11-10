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

    /** Creates the factory categories of a user, once (D-22); does nothing if she already has any. */
    @Transactional
    public void seedFactoryCategories(Long userId) {
        if (categories.existsByUserId(userId)) {
            return;
        }
        categories.saveAll(DefaultCategories.ALL.stream()
                .map(entry -> new Category(userId, entry.name(), entry.description()))
                .toList());
    }
}
