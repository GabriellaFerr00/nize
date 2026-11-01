package br.com.nize.api;

import br.com.nize.domain.AppUser;
import br.com.nize.repository.AppUserRepository;
import br.com.nize.security.CurrentUser;
import br.com.nize.service.OnboardingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AppUserRepository users;
    private final OnboardingService onboarding;

    public MeController(AppUserRepository users, OnboardingService onboarding) {
        this.users = users;
        this.onboarding = onboarding;
    }

    @GetMapping
    public MeResponse me() {
        AppUser user = users.findById(CurrentUser.id()).orElseThrow();
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.isOnboardingSeen());
    }

    /** Idempotent; the user is always the token's user. */
    @PostMapping("/onboarding-seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onboardingSeen() {
        onboarding.markSeen(CurrentUser.id());
    }
}
