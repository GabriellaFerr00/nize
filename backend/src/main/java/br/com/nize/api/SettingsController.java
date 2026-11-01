package br.com.nize.api;

import br.com.nize.domain.Settings;
import br.com.nize.security.CurrentUser;
import br.com.nize.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsResponse get() {
        return toResponse(service.get(CurrentUser.id()));
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return toResponse(service.update(CurrentUser.id(), request.currentYear(), request.autoInstallments()));
    }

    private static SettingsResponse toResponse(Settings settings) {
        return new SettingsResponse(settings.getCurrentYear(), settings.isAutoInstallments());
    }
}
