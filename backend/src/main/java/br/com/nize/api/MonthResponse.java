package br.com.nize.api;

import br.com.nize.service.MonthService;

import java.util.List;

/** Everything the month screen shows; money in cents, all computed by the back-end (the front never recalculates). */
public record MonthResponse(String month, MonthService.Totals totals, boolean closingsEnabled,
                            List<PositionResponse> positions, MonthService.Summaries summaries,
                            List<ExpenseResponse> expenses, List<IncomeResponse> incomes) {

    /** What is left (positive) or missing (negative) up to {@code day}, inclusive. */
    public record PositionResponse(int day, long balanceCents) {
    }

    static MonthResponse from(MonthService.MonthView view) {
        return new MonthResponse(view.month(), view.totals(), view.closingsEnabled(),
                view.positions().stream().map(p -> new PositionResponse(p.day(), p.balanceCents())).toList(),
                view.summaries(),
                view.expenses().stream().map(e -> ExpenseResponse.from(e, null)).toList(),
                view.incomes().stream().map(IncomeResponse::from).toList());
    }
}
