package br.com.nize.api;

import br.com.nize.service.MonthService;

import java.util.List;

/** Everything the month screen shows; money in cents, all computed by the back-end (the front never recalculates). */
public record MonthResponse(String month, MonthService.Totals totals, boolean closingsEnabled,
                            List<PositionResponse> positions, MonthService.Summaries summaries,
                            List<ExpenseResponse> expenses, List<IncomeResponse> incomes) {

    /** Sum of the expenses from {@code fromDay} to {@code toDay}, inclusive (REQ-030). */
    public record PositionResponse(int fromDay, int toDay, long totalExpensesCents) {
    }

    static MonthResponse from(MonthService.MonthView view) {
        return new MonthResponse(view.month(), view.totals(), view.closingsEnabled(),
                view.positions().stream().map(p -> new PositionResponse(p.fromDay(), p.toDay(), p.totalExpensesCents())).toList(),
                view.summaries(),
                view.expenses().stream().map(e -> ExpenseResponse.from(e, null)).toList(),
                view.incomes().stream().map(IncomeResponse::from).toList());
    }
}
