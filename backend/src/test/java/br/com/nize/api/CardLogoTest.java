package br.com.nize.api;

import br.com.nize.IsolationTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The user's own card logo (TASK-069, REQ-052, D-21): checked by its bytes, ≤ 200 KB, only for its owner. */
class CardLogoTest extends IsolationTestBase {

    private static final int YEAR = Year.now().getValue();
    private static final String LIST = "/api/years/" + YEAR + "/cards";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private static byte[] png(int size) {
        byte[] data = new byte[size];
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(head, 0, data, 0, head.length);
        Arrays.fill(data, head.length, size, (byte) 3);
        return data;
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    }

    private long createCard(TestUser user, String name) throws Exception {
        String body = as(user, post(LIST).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"dueDay\":10}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions upload(TestUser user, long card, byte[] data, String type)
            throws Exception {
        return as(user, put("/api/cards/" + card + "/logo").contentType(type).content(data));
    }

    @Test
    void anImageIsStoredAndServedWithItsRealType() throws Exception {
        long card = createCard(userA, "Nubank");
        as(userA, get(LIST)).andExpect(jsonPath("$[0].hasLogo").value(false));

        // the header says PNG, the bytes say JPEG: the bytes win
        upload(userA, card, jpeg(), "image/png").andExpect(status().isNoContent());

        as(userA, get("/api/cards/" + card + "/logo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-cache, private"))
                .andExpect(content().bytes(jpeg()));
        as(userA, get(LIST)).andExpect(jsonPath("$[0].hasLogo").value(true));
    }

    @Test
    void sendingAgainReplacesTheImage() throws Exception {
        long card = createCard(userA, "Nubank");
        upload(userA, card, jpeg(), "image/jpeg").andExpect(status().isNoContent());
        upload(userA, card, png(100), "image/png").andExpect(status().isNoContent());

        as(userA, get("/api/cards/" + card + "/logo")).andExpect(header().string("Content-Type", "image/png"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_logo WHERE card_id = ?", Integer.class, card)).isEqualTo(1);
    }

    @Test
    void svgAndOtherFilesAreRefused() throws Exception {
        long card = createCard(userA, "Nubank");
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

        upload(userA, card, svg, "image/svg+xml").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        upload(userA, card, "GIF89a...".getBytes(StandardCharsets.ISO_8859_1), "image/gif")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        upload(userA, card, new byte[0], "image/png").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        as(userA, get("/api/cards/" + card + "/logo")).andExpect(status().isNotFound());
    }

    @Test
    void upTo200KbIsAcceptedAndMoreIsRefused() throws Exception {
        long card = createCard(userA, "Nubank");

        upload(userA, card, png(200 * 1024), "image/png").andExpect(status().isNoContent());
        upload(userA, card, png(200 * 1024 + 1), "image/png").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        as(userA, get("/api/cards/" + card + "/logo")).andExpect(content().bytes(png(200 * 1024)));
    }

    @Test
    void theImageCanBeRemoved() throws Exception {
        long card = createCard(userA, "Nubank");
        upload(userA, card, jpeg(), "image/jpeg").andExpect(status().isNoContent());

        as(userA, delete("/api/cards/" + card + "/logo")).andExpect(status().isNoContent());

        as(userA, get("/api/cards/" + card + "/logo")).andExpect(status().isNotFound());
        as(userA, get(LIST)).andExpect(jsonPath("$[0].hasLogo").value(false));
    }

    @Test
    void deletingTheCardDeletesItsImage() throws Exception {
        long card = createCard(userA, "Nubank");
        upload(userA, card, jpeg(), "image/jpeg").andExpect(status().isNoContent());

        as(userA, delete("/api/cards/" + card)).andExpect(status().isNoContent());

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_logo", Integer.class)).isZero();
    }

    @Test
    void aNewYearKeepsTheCardImage() throws Exception {
        long card = createCard(userA, "Nubank");
        upload(userA, card, jpeg(), "image/jpeg").andExpect(status().isNoContent());

        as(userA, post("/api/years/" + (YEAR + 1))).andExpect(status().isCreated());

        String next = as(userA, get("/api/years/" + (YEAR + 1) + "/cards")).andReturn().getResponse().getContentAsString();
        long copy = ((Number) JsonPath.read(next, "$[0].id")).longValue();
        assertThat(copy).isNotEqualTo(card);
        as(userA, get("/api/cards/" + copy + "/logo")).andExpect(status().isOk()).andExpect(content().bytes(jpeg()));
    }

    @Test
    void onlyTheOwnerCanSendSeeOrRemoveTheImage() throws Exception {
        long cardOfA = createCard(userA, "Nubank");
        long cardOfB = createCard(userB, "Nubank");
        upload(userA, cardOfA, jpeg(), "image/jpeg").andExpect(status().isNoContent());
        upload(userB, cardOfB, jpeg(), "image/jpeg").andExpect(status().isNoContent());

        assertOnlyOwnerCanAccess(user -> get("/api/cards/" + (user.id() == userA.id() ? cardOfA : cardOfB) + "/logo"));
        upload(userB, cardOfA, png(50), "image/png").andExpect(status().isNotFound());
        as(userB, delete("/api/cards/" + cardOfA + "/logo")).andExpect(status().isNotFound());

        as(userA, get("/api/cards/" + cardOfA + "/logo")).andExpect(content().bytes(jpeg()));
    }

    @Test
    void anImageNeedsASignedInUser() throws Exception {
        long card = createCard(userA, "Nubank");
        mvc.perform(get("/api/cards/" + card + "/logo")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/cards/" + card + "/logo").contentType("image/png").content(png(20)))
                .andExpect(status().isUnauthorized());
    }
}
