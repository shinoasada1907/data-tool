package com.universalimporter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Dependency direction from design D1: api → application → domain ← infrastructure,
 * and the domain stays plain Java.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.universalimporter");

    @Test
    void domain_only_depends_on_jdk() {
        classes().that().resideInAPackage("com.universalimporter.domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "com.universalimporter.domain..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    /** BE-F11: when things run is a technical concern; the use cases stay callable directly. */
    @Test
    void scheduling_lives_in_infrastructure() {
        methods().that().areAnnotatedWith(Scheduled.class)
                .should().beDeclaredInClassesThat().resideInAPackage("com.universalimporter.infrastructure..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
        classes().that().implement(SchedulingConfigurer.class)
                .should().resideInAPackage("com.universalimporter.infrastructure..")
                .check(PRODUCTION_CLASSES);
        classes().that().areAnnotatedWith(EnableScheduling.class)
                .should().resideInAPackage("com.universalimporter.infrastructure..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void layers_respect_dependency_direction() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("api").definedBy("com.universalimporter.api..")
                .layer("application").definedBy("com.universalimporter.application..")
                .layer("domain").definedBy("com.universalimporter.domain..")
                .layer("infrastructure").definedBy("com.universalimporter.infrastructure..")
                .whereLayer("api").mayNotBeAccessedByAnyLayer()
                .whereLayer("application").mayOnlyBeAccessedByLayers("api", "infrastructure")
                .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("domain").mayOnlyBeAccessedByLayers("api", "application", "infrastructure")
                .check(PRODUCTION_CLASSES);
    }
}
