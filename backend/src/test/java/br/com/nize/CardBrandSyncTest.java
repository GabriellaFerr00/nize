package br.com.nize;

import br.com.nize.domain.CardBrand;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The card brands the API accepts are the ones the screen offers (REQ-052, D-21): same keys, same order, and every
 * brand marked with an icon has its CC0 SVG in frontend/public/card-brands.
 */
class CardBrandSyncTest {

    private static final Path FRONT = Path.of("..", "frontend");

    @Test
    void backAndFrontListTheSameBrands() throws IOException {
        String ts = Files.readString(FRONT.resolve("src/lib/cardBrands.ts"));
        Matcher m = Pattern.compile("key: '([a-z0-9]+)'").matcher(ts);
        List<String> front = new ArrayList<>();
        while (m.find()) {
            front.add(m.group(1));
        }
        assertThat(front).containsExactlyElementsOf(CardBrand.KEYS);
        assertThat(CardBrand.PATTERN).isEqualTo(String.join("|", CardBrand.KEYS));
    }

    @Test
    void everyBrandWithAnIconHasItsFileAndNoScript() throws IOException {
        String ts = Files.readString(FRONT.resolve("src/lib/cardBrands.ts"));
        Matcher m = Pattern.compile("key: '([a-z0-9]+)'[^}]*icon: true").matcher(ts);
        int icons = 0;
        while (m.find()) {
            Path svg = FRONT.resolve("public/card-brands/" + m.group(1) + ".svg");
            assertThat(svg).exists();
            assertThat(Files.readString(svg).toLowerCase()).doesNotContain("<script", "href", "onload");
            icons++;
        }
        assertThat(icons).isPositive();
    }
}
