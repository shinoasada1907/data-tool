package com.universaldatatools.platform.config;

import com.universaldatatools.core.format.xlsx.XlsxLimits;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.core.table.TableWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** core.format has no Spring; platform.config makes its beans and binds toolbox.format.xlsx (core-01 TD2). */
class FormatConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FormatConfig.class);

    @Test
    void xlsx_limits_default_to_the_v0_1_values() {
        runner.run(context -> assertThat(context.getBean(XlsxLimits.class))
                .isEqualTo(new XlsxLimits(200L * 1024 * 1024, 100, 10_000)));
    }

    @Test
    void xlsx_limits_bind_from_the_toolbox_prefix() {
        runner.withPropertyValues("toolbox.format.xlsx.max-entries=5", "toolbox.format.xlsx.max-uncompressed-size=1MB")
                .run(context -> assertThat(context.getBean(XlsxLimits.class))
                        .isEqualTo(new XlsxLimits(1024 * 1024, 100, 5)));
    }

    @Test
    void a_reader_and_a_writer_per_format_are_beans() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(TableReader.class)).hasSize(3);
            assertThat(context.getBeansOfType(TableWriter.class)).hasSize(3);
        });
    }
}
