package br.com.nize;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFR-004 / REQ-004: every data controller has a test that extends {@link IsolationTestBase}
 * and is named after it (e.g. CardController → CardApiTest, SettingsController → MeAndSettingsTest).
 */
class IsolationCoverageTest {

    /** Endpoints that serve no user data. */
    private static final Set<String> NOT_DATA = Set.of("AuthController");

    @Test
    void everyDataControllerHasAnIsolationTest() {
        JavaClasses classes = new ClassFileImporter().importPackages("br.com.nize");
        List<String> isolationTests = classes.stream()
                .filter(c -> c.isAssignableTo(IsolationTestBase.class) && !c.getSimpleName().equals("IsolationTestBase"))
                .map(JavaClass::getSimpleName)
                .toList();

        List<String> uncovered = classes.stream()
                .filter(c -> c.getPackageName().equals("br.com.nize.api") && c.isAnnotatedWith(RestController.class))
                .map(JavaClass::getSimpleName)
                .filter(name -> !NOT_DATA.contains(name))
                .filter(name -> isolationTests.stream().noneMatch(t -> t.contains(name.replace("Controller", ""))))
                .toList();

        assertThat(isolationTests).isNotEmpty();
        assertThat(uncovered).as("controllers without a test extending IsolationTestBase").isEmpty();
    }
}
