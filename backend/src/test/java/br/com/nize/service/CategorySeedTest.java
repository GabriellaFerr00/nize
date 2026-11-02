package br.com.nize.service;

import br.com.nize.IsolationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Factory categories (TASK-014): REQ-010, REQ-045. */
class CategorySeedTest extends IsolationTestBase {

    private static final List<String> FACTORY_NAMES = List.of(
            "Mercado", "Necessidades", "Eletrônicos", "Assinaturas", "Roupa", "Beleza", "Presentes", "Saúde",
            "Despesas eventuais", "Desenvolvimento", "Uber/transporte", "IFood/restaurante", "Lazer", "Aluguel",
            "Contas");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CategorySeeder seeder;

    private List<Map<String, Object>> categoriesOf(long userId, int year) {
        entityManager.flush();
        return jdbc.queryForList(
                "SELECT name, description, active FROM category WHERE user_id = ? AND year_number = ? ORDER BY id",
                userId, year);
    }

    @Test
    void newAccountHasTheFifteenFactoryCategoriesInTheCurrentYear() {
        List<Map<String, Object>> rows = categoriesOf(userA.id(), Year.now().getValue());

        assertThat(rows).extracting(r -> r.get("name")).containsExactlyElementsOf(FACTORY_NAMES);
        assertThat(rows).allSatisfy(r -> assertThat(r.get("active")).isEqualTo(true));
    }

    @Test
    void everyCategoryHasADescriptionThatFitsTheColumn() {
        assertThat(categoriesOf(userA.id(), Year.now().getValue())).allSatisfy(r ->
                assertThat((String) r.get("description")).isNotBlank().hasSizeLessThanOrEqualTo(255));
    }

    @Test
    void mainhaIsAPersonNotACategory() {
        assertThat(categoriesOf(userA.id(), Year.now().getValue())).extracting(r -> r.get("name"))
                .doesNotContain("Mainha");
    }

    @Test
    void eachUserGetsTheirOwnSeparateSet() {
        int year = Year.now().getValue();
        assertThat(categoriesOf(userA.id(), year)).hasSize(15);
        assertThat(categoriesOf(userB.id(), year)).hasSize(15);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category", Integer.class)).isEqualTo(30);
    }

    @Test
    void seedingAYearAddsFifteenForThatYearAndIsIdempotent() {
        seeder.seedFactoryCategories(userA.id(), 2031);
        seeder.seedFactoryCategories(userA.id(), 2031);

        assertThat(categoriesOf(userA.id(), 2031)).hasSize(15);
        assertThat(categoriesOf(userA.id(), Year.now().getValue())).hasSize(15);
    }
}
