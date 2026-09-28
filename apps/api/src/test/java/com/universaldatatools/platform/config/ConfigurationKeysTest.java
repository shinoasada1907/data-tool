package com.universaldatatools.platform.config;

import com.universaldatatools.core.format.xlsx.XlsxLimits;
import com.universaldatatools.platform.storage.StorageProperties;
import com.universaldatatools.tools.importer.application.importsession.CleanupProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The toolbox.* keys of the real application.yaml, with the IMPORTER_* environment variables of V0.1 still honoured
 * (spec: toolbox-platform). Only the main file is loaded: the test one moves storage elsewhere.
 */
class ConfigurationKeysTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=classpath:/application.yaml")
            .withUserConfiguration(Properties.class, FormatConfig.class);

    @Test
    void old_storage_variable_still_works() {
        runner.withPropertyValues("IMPORTER_STORAGE_DIR=/x/old")
                .run(context -> assertThat(context.getBean(StorageProperties.class).dir()).isEqualTo(Path.of("/x/old")));
    }

    @Test
    void new_storage_variable_wins() {
        runner.withPropertyValues("TOOLBOX_STORAGE_DIR=/x/new", "IMPORTER_STORAGE_DIR=/x/old")
                .run(context -> assertThat(context.getBean(StorageProperties.class).dir()).isEqualTo(Path.of("/x/new")));
    }

    @Test
    void old_ttl_variable_without_unit_is_hours() {
        runner.withPropertyValues("IMPORTER_SESSION_TTL=24")
                .run(context -> assertThat(context.getBean(CleanupProperties.class).sessionTtl())
                        .isEqualTo(Duration.ofHours(24)));
    }

    @Test
    void new_ttl_variable_wins() {
        runner.withPropertyValues("TOOLBOX_IMPORTER_SESSION_TTL=2h", "IMPORTER_SESSION_TTL=24")
                .run(context -> assertThat(context.getBean(CleanupProperties.class).sessionTtl())
                        .isEqualTo(Duration.ofHours(2)));
    }

    @Test
    void xlsx_limits_take_old_and_new_variables() {
        runner.withPropertyValues("IMPORTER_XLSX_MAX_ENTRIES=7")
                .run(context -> assertThat(context.getBean(XlsxLimits.class).maxEntries()).isEqualTo(7));
        runner.withPropertyValues("TOOLBOX_XLSX_MAX_ENTRIES=5", "IMPORTER_XLSX_MAX_ENTRIES=7")
                .run(context -> assertThat(context.getBean(XlsxLimits.class).maxEntries()).isEqualTo(5));
    }

    @Test
    void old_yaml_keys_are_no_longer_read() {
        runner.withPropertyValues("importer.storage.dir=/x/legacy-key")
                .run(context -> assertThat(context.getBean(StorageProperties.class).dir())
                        .isEqualTo(Path.of(System.getProperty("java.io.tmpdir"), "universal-importer")));
    }

    @Test
    void application_is_named_after_the_toolbox() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("universal-data-tools"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({StorageProperties.class, CleanupProperties.class})
    static class Properties {
    }
}
