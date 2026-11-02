package br.com.nize.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** design.md ("Categorias de fábrica") and DefaultCategories must say the same thing. */
class DefaultCategoriesSyncTest {

    private static final Pattern ROW = Pattern.compile("^\\| (\\d+) \\| (.+?) \\| (.+?) \\|$");

    @Test
    void codeMatchesTheTableInDesignMd() throws IOException {
        Path design = Path.of("..", "design.md");
        String text = Files.readString(design, StandardCharsets.UTF_8);
        String section = text.substring(text.indexOf("## Categorias de fábrica"), text.indexOf("## Regras de cálculo"));

        List<DefaultCategories.Entry> documented = new ArrayList<>();
        for (String line : section.split("\n")) {
            Matcher m = ROW.matcher(line.strip());
            if (m.matches()) {
                documented.add(new DefaultCategories.Entry(m.group(2), m.group(3)));
            }
        }

        assertThat(documented).hasSize(15).containsExactlyElementsOf(DefaultCategories.ALL);
    }
}
