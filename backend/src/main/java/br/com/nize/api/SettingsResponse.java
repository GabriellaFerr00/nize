package br.com.nize.api;

import java.util.List;

/** {@code closingDays} in ascending order (REQ-048). */
public record SettingsResponse(int currentYear, boolean autoInstallments, boolean closingsEnabled,
                               List<Integer> closingDays) {
}
