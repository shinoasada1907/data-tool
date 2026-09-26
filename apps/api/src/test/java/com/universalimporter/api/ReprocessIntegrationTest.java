package com.universalimporter.api;

import com.jayway.jsonpath.JsonPath;
import com.universalimporter.support.E2eFlow;
import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.HttpTestClient.Response;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Change the configuration of a processed session, then process again (BE-F11 task 8). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ReprocessIntegrationTest {

    private static final String NO_VALIDATIONS = "{\"validations\":[]}";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @Test
    void dropping_the_unique_rule_turns_the_duplicate_row_valid() {
        HttpTestClient http = new HttpTestClient(port);
        String session = E2eFlow.upload(http);
        E2eFlow.configureCsv(http, session);
        Response first = http.post(session + "/process");
        assertThat((Integer) JsonPath.read(first.body(), "$.valid")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(first.body(), "$.invalid")).isEqualTo(3);

        Response change = E2eFlow.put(http, session + "/validations", NO_VALIDATIONS);
        assertThat((String) JsonPath.read(change.body(), "$.session.status")).isEqualTo("READY");
        assertThat(http.get(session + "/result").status()).isEqualTo(409);

        Response second = http.post(session + "/process");
        assertThat((Integer) JsonPath.read(second.body(), "$.valid")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(second.body(), "$.invalid")).isEqualTo(2);
        Response valid = http.get(session + "/result?view=valid");
        assertThat((List<Integer>) JsonPath.read(valid.body(), "$.rows[*].rowNumber")).containsExactly(2, 4);
        assertThat((String) JsonPath.read(valid.body(), "$.rows[1].values.email")).isEqualTo("an@example.com");

        Response same = E2eFlow.put(http, session + "/validations", NO_VALIDATIONS);
        assertThat((String) JsonPath.read(same.body(), "$.session.status")).isEqualTo("PROCESSED");
        Response kept = http.get(session + "/result?view=valid");
        assertThat(kept.status()).isEqualTo(200);
        assertThat((List<Integer>) JsonPath.read(kept.body(), "$.rows[*].rowNumber")).containsExactly(2, 4);
    }
}
