package com.universaldatatools;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Module boundaries of the toolbox (design core-01 TD1, spec: toolbox-platform): core is plain Java, core.format
 * only adds file-format libraries, platform knows no tool, tools never depend on each other, and each tool keeps
 * api → application → domain ← infrastructure.
 */
class ArchitectureTest {

    private static final String ROOT = "com.universaldatatools";
    private static final String CORE = ROOT + ".core..";
    private static final String CORE_FORMAT = ROOT + ".core.format..";
    private static final String PLATFORM = ROOT + ".platform..";
    private static final String TOOLS = ROOT + ".tools..";

    /** Tools that exist so far; each new tool adds its name. */
    private static final List<String> TOOL_NAMES = List.of("importer", "converter");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void core_is_plain_java() {
        classes().that().resideInAPackage(CORE).and().resideOutsideOfPackage(CORE_FORMAT)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", CORE, "com.google.re2j..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void core_format_uses_only_format_libraries() {
        classes().that().resideInAPackage(CORE_FORMAT)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", CORE, "org.apache.commons.csv..",
                        "org.apache.commons.compress..", "org.dhatim.fastexcel..", "tools.jackson..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void core_has_no_framework_or_outer_layers() {
        noClasses().that().resideInAPackage(CORE)
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", PLATFORM, TOOLS)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void platform_does_not_know_tools() {
        noClasses().that().resideInAPackage(PLATFORM)
                .should().dependOnClassesThat().resideInAPackage(TOOLS)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void tools_do_not_depend_on_each_other() {
        slices().matching(ROOT + ".tools.(*)..").should().notDependOnEachOther()
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void each_tool_is_layered() {
        for (String tool : TOOL_NAMES) {
            String base = ROOT + ".tools." + tool;
            layeredArchitecture().consideringOnlyDependenciesInLayers()
                    .withOptionalLayers(true)
                    .layer("api").definedBy(base + ".api..")
                    .layer("application").definedBy(base + ".application..")
                    .layer("domain").definedBy(base + ".domain..")
                    .layer("infrastructure").definedBy(base + ".infrastructure..")
                    .whereLayer("api").mayNotBeAccessedByAnyLayer()
                    .whereLayer("application").mayOnlyBeAccessedByLayers("api", "infrastructure")
                    .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer()
                    .whereLayer("domain").mayOnlyBeAccessedByLayers("api", "application", "infrastructure")
                    .check(PRODUCTION_CLASSES);
            // A tool without rules of its own (the converter) has no domain package.
            classes().that().resideInAPackage(base + ".domain..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("java..", CORE, base + "..")
                    .allowEmptyShould(true)
                    .check(PRODUCTION_CLASSES);
        }
    }

    /** When things run is a technical concern; the use cases stay callable directly (BE-F11). */
    @Test
    void scheduling_lives_in_platform_or_tool_infrastructure() {
        String[] allowed = {PLATFORM, ROOT + ".tools.*.infrastructure.."};
        methods().that().areAnnotatedWith(Scheduled.class)
                .should().beDeclaredInClassesThat().resideInAnyPackage(allowed)
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
        classes().that().implement(SchedulingConfigurer.class)
                .should().resideInAnyPackage(allowed)
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
        classes().that().areAnnotatedWith(EnableScheduling.class)
                .should().resideInAnyPackage(allowed)
                .check(PRODUCTION_CLASSES);
    }
}
