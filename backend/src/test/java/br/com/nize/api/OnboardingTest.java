package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/me/onboarding-seen (TASK-011): REQ-005, REQ-004. */
class OnboardingTest extends IsolationTestBase {

    @Test
    void newUserHasNotSeenTheOnboarding() throws Exception {
        as(userA, get("/api/me")).andExpect(jsonPath("$.onboardingSeen").value(false));
    }

    @Test
    void markingItSeenPersistsAndIsIdempotent() throws Exception {
        as(userA, post("/api/me/onboarding-seen")).andExpect(status().isNoContent());
        as(userA, get("/api/me")).andExpect(jsonPath("$.onboardingSeen").value(true));

        as(userA, post("/api/me/onboarding-seen")).andExpect(status().isNoContent());
        as(userA, get("/api/me")).andExpect(jsonPath("$.onboardingSeen").value(true));
    }

    @Test
    void onlyTheCallersFlagChanges() throws Exception {
        as(userA, post("/api/me/onboarding-seen").param("userId", String.valueOf(userB.id())))
                .andExpect(status().isNoContent());

        as(userA, get("/api/me")).andExpect(jsonPath("$.onboardingSeen").value(true));
        as(userB, get("/api/me")).andExpect(jsonPath("$.onboardingSeen").value(false));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/me/onboarding-seen")).andExpect(status().isUnauthorized());
    }
}
