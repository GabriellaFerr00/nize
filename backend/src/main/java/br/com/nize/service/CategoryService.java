package br.com.nize.service;

import br.com.nize.domain.Category;
import br.com.nize.repository.CategoryMonthLimitRepository;
import br.com.nize.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Category rules. The user id always comes from the token (D-6); other users' rows are "not found". */
@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final CategoryUsage usage;
    private final CategoryMonthLimitRepository monthLimits;

    public CategoryService(CategoryRepository categories, CategoryUsage usage, CategoryMonthLimitRepository monthLimits) {
        this.categories = categories;
        this.usage = usage;
        this.monthLimits = monthLimits;
    }

    @Transactional(readOnly = true)
    public List<Category> list(Long userId, boolean activeOnly) {
        return activeOnly
                ? categories.findByUserIdAndActiveOrderByIdAsc(userId, true)
                : categories.findByUserIdOrderByIdAsc(userId);
    }

    @Transactional
    public Category create(Long userId, String name, String description, Long monthlyLimitCents,
                           String color) {
        if (categories.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new CategoryNameTakenException();
        }
        Category category = new Category(userId, name, description);
        category.setMonthlyLimitCents(monthlyLimitCents);
        category.setColor(color);
        return categories.save(category);
    }

    @Transactional
    /** Full replacement (REQ-011): a missing {@code monthlyLimitCents} removes the limit (REQ-051). */
    public Category update(Long userId, Long id, String name, String description, boolean active,
                           Long monthlyLimitCents, String color) {
        Category category = categories.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (categories.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, category.getId())) {
            throw new CategoryNameTakenException();
        }
        category.update(name, description, active);
        category.setMonthlyLimitCents(monthlyLimitCents);
        category.setColor(color);
        return category;
    }

    /** Deletes right away if unused; otherwise keeps it and reports how many expenses use it (REQ-012). */
    @Transactional
    public void delete(Long userId, Long id) {
        Category category = categories.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        long expenses = usage.countExpenses(userId, category.getId());
        if (expenses > 0) {
            throw new CategoryInUseException(expenses);
        }
        monthLimits.deleteAll(monthLimits.findByUserIdAndCategoryId(userId, category.getId()));
        categories.delete(category);
    }
}
