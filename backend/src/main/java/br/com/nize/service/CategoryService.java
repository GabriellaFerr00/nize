package br.com.nize.service;

import br.com.nize.domain.Category;
import br.com.nize.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Category rules. The user id always comes from the token (D-6); other users' rows are "not found". */
@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final YearGuard yearGuard;
    private final CategoryUsage usage;

    public CategoryService(CategoryRepository categories, YearGuard yearGuard, CategoryUsage usage) {
        this.categories = categories;
        this.yearGuard = yearGuard;
        this.usage = usage;
    }

    @Transactional(readOnly = true)
    public List<Category> list(Long userId, int year, boolean activeOnly) {
        yearGuard.requireYear(userId, year);
        return activeOnly
                ? categories.findByUserIdAndYearNumberAndActiveOrderByIdAsc(userId, year, true)
                : categories.findByUserIdAndYearNumberOrderByIdAsc(userId, year);
    }

    @Transactional
    public Category create(Long userId, int year, String name, String description, Long monthlyLimitCents,
                           String color) {
        yearGuard.requireYear(userId, year);
        if (categories.existsByUserIdAndYearNumberAndNameIgnoreCase(userId, year, name)) {
            throw new CategoryNameTakenException();
        }
        Category category = new Category(userId, year, name, description);
        category.setMonthlyLimitCents(monthlyLimitCents);
        category.setColor(color);
        return categories.save(category);
    }

    @Transactional
    /** Full replacement (REQ-011): a missing {@code monthlyLimitCents} removes the limit (REQ-051). */
    public Category update(Long userId, Long id, String name, String description, boolean active,
                           Long monthlyLimitCents, String color) {
        Category category = categories.findByIdAndUserId(id, userId).orElseThrow(ResourceNotFoundException::new);
        if (categories.existsByUserIdAndYearNumberAndNameIgnoreCaseAndIdNot(
                userId, category.getYearNumber(), name, category.getId())) {
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
        categories.delete(category);
    }
}
