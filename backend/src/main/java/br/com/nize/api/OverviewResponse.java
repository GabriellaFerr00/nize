package br.com.nize.api;

import br.com.nize.service.OverviewService;

import java.util.List;

/** The 12 months of a year, January first; money in cents, all computed by the back-end (REQ-039). */
public record OverviewResponse(int year, List<OverviewService.MonthEntry> months) {
}
