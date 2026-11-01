package br.com.nize.service;

import br.com.nize.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    private final AppUserRepository users;

    public OnboardingService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public void markSeen(Long userId) {
        users.findById(userId).orElseThrow().markOnboardingSeen();
    }
}
