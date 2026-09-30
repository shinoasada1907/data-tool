package com.universaldatatools.tools.converter;

import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.platform.dataset.Dataset;
import com.universaldatatools.platform.dataset.DatasetService;
import com.universaldatatools.platform.dataset.SourceRef;
import com.universaldatatools.platform.output.OutputDto;
import com.universaldatatools.support.TestcontainersConfiguration;
import com.universaldatatools.tools.converter.application.ConverterService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perf smoke of core-01 TD20: 200 000 rows × 10 columns of CSV to JSON in under 15 seconds. Left out of normal
 * runs; run it with {@code ./mvnw test -Dtest=ConverterPerfTest -DexcludedGroups=none}.
 */
@Tag("perf")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConverterPerfTest {

    @Autowired
    DatasetService datasets;

    @Autowired
    ConverterService converter;

    @Test
    void two_hundred_thousand_rows_convert_in_time() throws IOException {
        StringBuilder csv = new StringBuilder("c0,c1,c2,c3,c4,c5,c6,c7,c8,c9\n");
        for (int row = 0; row < 200_000; row++) {
            for (int column = 0; column < 10; column++) {
                csv.append(column == 0 ? "" : ",").append(column % 2 == 0 ? row + column : "v" + row);
            }
            csv.append('\n');
        }
        Dataset dataset = datasets.upload("big.csv",
                new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));

        long start = System.nanoTime();
        try (ConverterService.Prepared prepared = converter.prepare(
                new SourceRef(dataset.id(), ReadOptions.defaults()), new OutputDto("JSON", null, null, null))) {
            prepared.body().writeTo(OutputStream.nullOutputStream());
        }
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        System.out.println("PERF converter 200k x 10 CSV -> JSON: " + took.toMillis() + " ms");
        assertThat(took).isLessThan(Duration.ofSeconds(15));
    }
}
