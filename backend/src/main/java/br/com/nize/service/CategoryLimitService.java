package br.com.nize.service;

import br.com.nize.domain.Category;
import br.com.nize.domain.CategoryMonthLimit;
import br.com.nize.repository.CategoryMonthLimitRepository;
import br.com.nize.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Limits of a month (REQ-051, D-22): each category has a default limit for every month and may have its own limit
 * in one month, which wins. Removing the month's limit brings the default back.
 */
@Service
public class CategoryLimitService {

    /** {@code monthCents} null = the month uses the default; {@code defaultCents} null = no default. */
    public record MonthLimit(Long categoryId, String categoryName, Long defaultCents, Long monthCents) {
    }

    private final CategoryRepository categories;
    private final CategoryMonthLimitRepository limits;

    public CategoryLimitService(CategoryRepository categories, CategoryMonthLimitRepository limits) {
        this.categories = categories;
        this.limits = limits;
    }

    /** Active categories, plus inactive ones that have a limit of their own in this month. */
    @Transactional(readOnly = true)
    public List<MonthLimit> list(Long userId, String monthRef) {
        String month = ExpenseService.parseMonth(monthRef).toString();
        Map<Long, Long> own = new HashMap<>();
        limits.findByUserIdAndMonth(userId, month).forEach(l -> own.put(l.getCategoryId(), l.getLimitCents()));
        return categories.findByUserIdOrderByIdAsc(userId).stream()
                .filter(c -> c.isActive() || own.containsKey(c.getId()))
                .map(c -> new MonthLimit(c.getId(), c.getName(), c.getMonthlyLimitCents(), own.get(c.getId())))
                .toList();
    }

    @Transactional
    public void set(Long userId, String monthRef, Long categoryId, long limitCents) {
        YearMonth month = ExpenseService.parseMonth(monthRef);
        Category category = categories.findByIdAndUserId(categoryId, userId).orElseThrow(ResourceNotFoundException::new);
        limits.findByUserIdAndCategoryIdAndMonth(userId, category.getId(), month.toString())
                .ifPresentOrElse(l -> l.setLimitCents(limitCents),
                        () -> limits.save(new CategoryMonthLimit(category.getId(), month.toString(), userId, limitCents)));
    }

    @Transactional
    public void clear(Long userId, String monthRef, Long categoryId) {
        YearMonth month = ExpenseService.parseMonth(monthRef);
        Category category = categories.findByIdAndUserId(categoryId, userId).orElseThrow(ResourceNotFoundException::new);
        limits.findByUserIdAndCategoryIdAndMonth(userId, category.getId(), month.toString()).ifPresent(limits::delete);
    }
}
