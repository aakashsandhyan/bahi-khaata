package com.bahikhaata.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces the boundaries in design decision 1. The Gradle dependency graph already
 * makes most of these impossible to compile — the point of asserting them here is to
 * catch the moment someone adds the missing edge to a build file, which is how a
 * boundary quietly stops being true.
 *
 * <p>Rules are written explicitly rather than with {@code @AnalyzeClasses} so it is
 * visible at the call site exactly which classes each rule examines.
 *
 * <p>{@code allowEmptyShould(true)} is set because modules are still being populated
 * and ArchUnit fails a rule that matches no classes. Once every module carries real
 * code this should be removed, so that an empty module becomes a failure rather than
 * a silent pass.
 */
class ModuleBoundaryTest {

    private static final String CONTRACTS = "com.bahikhaata.contracts";
    private static final String BACKEND = "com.bahikhaata.backend";
    private static final String TERMINAL = "com.bahikhaata.terminal";
    private static final String DASHBOARD = "com.bahikhaata.dashboard";

    private static JavaClasses classesIn(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }

    @Test
    @DisplayName("terminal must not depend on backend")
    void terminalMustNotDependOnBackend() {
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BACKEND + "..")
                .because(
                        "the terminal reaches data only over localhost HTTP; a direct dependency "
                                + "would make the backend impossible to detach")
                .allowEmptyShould(true);

        rule.check(classesIn(TERMINAL));
    }

    @Test
    @DisplayName("dashboard must not depend on backend")
    void dashboardMustNotDependOnBackend() {
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BACKEND + "..")
                .because("the dashboard is a client of the backend, not part of it")
                .allowEmptyShould(true);

        rule.check(classesIn(DASHBOARD));
    }

    @Test
    @DisplayName("backend must not depend on terminal or dashboard")
    void backendMustNotDependOnItsClients() {
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(TERMINAL + "..", DASHBOARD + "..")
                .because("the backend serves its clients and must not know who they are")
                .allowEmptyShould(true);

        rule.check(classesIn(BACKEND));
    }

    @Test
    @DisplayName("contracts must not depend on any other module")
    void contractsMustNotDependOnOtherModules() {
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(BACKEND + "..", TERMINAL + "..", DASHBOARD + "..")
                .because("contracts is the shared wire format and must depend on nothing")
                .allowEmptyShould(true);

        rule.check(classesIn(CONTRACTS));
    }

    @Test
    @DisplayName("contracts must not use Spring, JPA, Hibernate or a JDBC driver")
    void contractsMustStayFreeOfPersistenceAndFramework() {
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "org.hibernate..",
                        "org.springframework..",
                        "org.flywaydb..",
                        "java.sql..",
                        "javax.sql..")
                .because(
                        "a JPA entity or Spring type in contracts leaks the persistence model into "
                                + "the wire format, making every schema change a breaking API change")
                .allowEmptyShould(true);

        rule.check(classesIn(CONTRACTS));
    }
}
