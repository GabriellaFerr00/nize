package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Year;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Closing days in /api/settings (TASK-061): REQ-048, REQ-004, NFR-004. */
class SettingsClosingsApiTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();

    private ResultActions save(TestUser user, String closings) throws Exception {
        return as(user, put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentYear\":%d,\"autoInstallments\":true%s}".formatted(YEAR, closings)));
    }

    @Test
    void aNewAccountHasClosingsOnWithThe15thAndTheLastDay() throws Exception {
        as(userA, get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingsEnabled").value(true))
                .andExpect(jsonPath("$.closingDays", contains(15, 31)));
    }

    @Test
    void anyNumberOfDaysIsSavedAndReturnedInAscendingOrder() throws Exception {
        save(userA, ",\"closingsEnabled\":true,\"closingDays\":[20,5,10]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDays", contains(5, 10, 20)));

        as(userA, get("/api/settings")).andExpect(jsonPath("$.closingDays", contains(5, 10, 20)));
    }

    @Test
    void anEmptyListIsAllowed() throws Exception {
        save(userA, ",\"closingDays\":[]").andExpect(status().isOk()).andExpect(jsonPath("$.closingDays", empty()));
    }

    @Test
    void turningClosingsOffKeepsTheSavedDays() throws Exception {
        save(userA, ",\"closingDays\":[1,25]").andExpect(status().isOk());

        save(userA, ",\"closingsEnabled\":false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingsEnabled").value(false))
                .andExpect(jsonPath("$.closingDays", contains(1, 25)));
        save(userA, ",\"closingsEnabled\":true")
                .andExpect(jsonPath("$.closingsEnabled").value(true))
                .andExpect(jsonPath("$.closingDays", contains(1, 25)));
    }

    @Test
    void leavingTheClosingFieldsOutKeepsThem() throws Exception {
        save(userA, ",\"closingsEnabled\":false,\"closingDays\":[7]").andExpect(status().isOk());

        save(userA, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingsEnabled").value(false))
                .andExpect(jsonPath("$.closingDays", contains(7)));
    }

    @Test
    void daysOutsideOneTo31OrRepeatedAreRejected() throws Exception {
        for (String days : new String[] {"[0]", "[32]", "[15,15]", "[5,null]"}) {
            save(userA, ",\"closingDays\":" + days)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fields.closingDays").exists());
        }
        as(userA, get("/api/settings")).andExpect(jsonPath("$.closingDays", contains(15, 31)));
    }

    @Test
    void eachUserSeesAndChangesOnlyTheirOwnClosings() throws Exception {
        save(userA, ",\"closingsEnabled\":false,\"closingDays\":[3]").andExpect(status().isOk());

        as(userB, get("/api/settings"))
                .andExpect(jsonPath("$.closingsEnabled").value(true))
                .andExpect(jsonPath("$.closingDays", contains(15, 31)));
        save(userB, ",\"closingDays\":[9]").andExpect(status().isOk());
        as(userA, get("/api/settings")).andExpect(jsonPath("$.closingDays", contains(3)));
    }
}
