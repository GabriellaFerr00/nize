package br.com.nize;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules (NFR-001, D-2): money is always a long in cents, and
 * br.com.nize.rules is pure Java.
 */
@AnalyzeClasses(packages = "br.com.nize", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Field names that hold money. Any field matching must be long/Long. */
    private static final String MONEY_NAME =
            "(?i).*(amount|cents|valor|value|total|balance|saldo|price|preco|money|dinheiro).*";

    private static final Set<String> FORBIDDEN_MONEY_TYPES = Set.of(
            "double", "float", "java.lang.Double", "java.lang.Float", "java.math.BigDecimal");

    @ArchTest
    static final ArchRule moneyFieldsAreNeverFloatingPointOrBigDecimal =
            fields().that().haveNameMatching(MONEY_NAME)
                    .should(notBeFloatingPointOrBigDecimal())
                    .because("money is an integer number of cents (NFR-001)");

    @ArchTest
    static final ArchRule rulesPackageDoesNotDependOnSpring =
            noClasses().that().resideInAPackage("br.com.nize.rules..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("calculation rules must be pure Java (design.md)");

    @ArchTest
    static final ArchRule rulesPackageDoesNotDependOnJpa =
            noClasses().that().resideInAPackage("br.com.nize.rules..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..", "javax.persistence..", "org.hibernate..")
                    .because("calculation rules must be pure Java (design.md)");

    private static ArchCondition<JavaField> notBeFloatingPointOrBigDecimal() {
        return new ArchCondition<>("not be double, float or BigDecimal") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                JavaClass type = field.getRawType();
                if (FORBIDDEN_MONEY_TYPES.contains(type.getName())) {
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " is " + type.getName() + "; use long cents"));
                }
            }
        };
    }
}
