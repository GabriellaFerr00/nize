package br.com.nize;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import br.com.nize.repository.AppUserRepository;
import br.com.nize.repository.CardRepository;
import br.com.nize.repository.CategoryRepository;
import br.com.nize.repository.ExpenseRepository;
import br.com.nize.repository.PasswordResetTokenRepository;
import br.com.nize.repository.PersonRepository;
import org.springframework.data.repository.query.Param;

import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Architecture rules (NFR-001, D-2): money is always a long in cents, and
 * br.com.nize.rules is pure Java. Isolation rules (NFR-004, D-6): the user id never comes from
 * the request and every query on user data is filtered by it.
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

    @ArchTest
    static final ArchRule requestDtosNeverCarryAUserId =
            noFields().that().areDeclaredInClassesThat().resideInAPackage("br.com.nize.api..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Request")
                    .should().haveNameMatching("(?i)user_?id")
                    .because("the user id comes only from the token via CurrentUser.id() (D-6)");

    @ArchTest
    static final ArchRule userDataQueriesAreFilteredByUserId =
            methods().that().areDeclaredInClassesThat().resideInAPackage("br.com.nize.repository..")
                    .and().areDeclaredInClassesThat().areNotAssignableTo(AppUserRepository.class)
                    .and().areDeclaredInClassesThat().areNotAssignableTo(PasswordResetTokenRepository.class)
                    .should(takeTheUserId())
                    .because("every query on user data filters by user_id (NFR-004)");

    @ArchTest
    static final ArchRule userDataIsNeverLoadedByIdAlone =
            noClasses().should().callMethodWhere(target(nameMatching("(find|exists|delete)ById|findAllById|deleteAllById"))
                            .and(target(owner(assignableTo(CardRepository.class)
                                    .or(assignableTo(CategoryRepository.class))
                                    .or(assignableTo(ExpenseRepository.class))
                                    .or(assignableTo(PersonRepository.class))))))
                    .because("use findByIdAndUserId so another user's row is a 404 (D-6); "
                            + "UserYear and LoadedMonth ids already contain the user id");

    private static ArchCondition<JavaMethod> takeTheUserId() {
        return new ArchCondition<>("take the user id (name with 'UserId' or @Param(\"userId\"))") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean byName = method.getName().contains("UserId");
                boolean byParam = method.getParameters().stream().anyMatch(ArchitectureTest::isUserIdParam);
                if (!byName && !byParam) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " does not filter by userId"));
                }
            }
        };
    }

    private static boolean isUserIdParam(JavaParameter parameter) {
        return parameter.isAnnotatedWith(Param.class)
                && "userId".equals(parameter.getAnnotationOfType(Param.class).value());
    }

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
