package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Year;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/me and GET/PUT /api/settings (TASK-007, TASK-008): REQ-004, REQ-006, REQ-009, NFR-004. */
class MeAndSettingsTest extends IsolationTestBase {

    private static final int THIS_YEAR = Year.now().getValue();

    private String settingsJson(int year, boolean auto) {
        return """
                {"currentYear":%d,"autoInstallments":%s}""".formatted(year, auto);
    }

    // ---- isolation helper itself (TASK-007) ----

    @Test
    void helperDetectsOwnerOnlyResources() throws Exception {
        assertOnlyOwnerCanAccess(user -> get("/api/test/owned/" + user.id()));
    }

    // ---- /api/me ----

    @Test
    void meReturnsTheCallerAndIgnoresAnyUserIdInTheRequest() throws Exception {
        as(userA, get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.id()))
                .andExpect(jsonPath("$.email").value(userA.email()))
                .andExpect(jsonPath("$.onboardingSeen").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        as(userB, get("/api/me").param("userId", String.valueOf(userA.id())))
                .andExpect(jsonPath("$.id").value(userB.id()))
                .andExpect(jsonPath("$.email").value(userB.email()));
    }

    // ---- settings: defaults and persistence (TASK-008) ----

    @Test
    void newAccountHasCurrentYearAndAutomaticInstallmentsOn() throws Exception {
        as(userA, get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentYear").value(THIS_YEAR))
                .andExpect(jsonPath("$.autoInstallments").value(true));
    }

    @Test
    void changingAutoInstallmentsPersistsAndIsReturnedByGet() throws Exception {
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson(THIS_YEAR, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoInstallments").value(false));

        as(userA, get("/api/settings"))
                .andExpect(jsonPath("$.currentYear").value(THIS_YEAR))
                .andExpect(jsonPath("$.autoInstallments").value(false));
    }

    @Test
    void currentYearMustBeAYearTheUserHas() throws Exception {
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson(THIS_YEAR + 5, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_YEAR"));

        as(userA, get("/api/settings")).andExpect(jsonPath("$.currentYear").value(THIS_YEAR));
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.currentYear").exists())
                .andExpect(jsonPath("$.fields.autoInstallments").exists());
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson(1800, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- settings: isolation (TASK-007) ----

    @Test
    void userBCannotReadOrChangeUserAsSettings() throws Exception {
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson(THIS_YEAR, false))).andExpect(status().isOk());

        // B sees its own defaults, not A's change
        as(userB, get("/api/settings")).andExpect(jsonPath("$.autoInstallments").value(true));

        // B tries to target A by smuggling A's id in the body: only B's own row may change
        as(userB, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"currentYear":%d,"autoInstallments":true}""".formatted(userA.id(), THIS_YEAR)))
                .andExpect(status().isOk());
        as(userA, get("/api/settings")).andExpect(jsonPath("$.autoInstallments").value(false));

        // and the other way round
        as(userB, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson(THIS_YEAR, false))).andExpect(status().isOk());
        as(userA, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"currentYear":%d,"autoInstallments":true}""".formatted(userB.id(), THIS_YEAR)))
                .andExpect(status().isOk());
        as(userB, get("/api/settings")).andExpect(jsonPath("$.autoInstallments").value(false));
    }

    @Test
    void settingsAndMeRequireAuthentication() throws Exception {
        mvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson(THIS_YEAR, true))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
