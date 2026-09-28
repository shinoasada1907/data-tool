package com.universalimporter.api;

import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Pins the importer's OpenAPI contract (paths and schemas, not the title) so that moving packages or adding tools
 * cannot change it unnoticed (spec: toolbox-platform). Run once with {@code -Dsnapshot.write=true} to record it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiContractSnapshotTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/contract/importer-openapi.json");
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @LocalServerPort
    int port;

    @Test
    void importer_contract_matches_the_snapshot() throws IOException {
        String body = new HttpTestClient(port).get("/v3/api-docs").body();
        String actual = JSON.writeValueAsString(importerContract(JSON.readValue(body, Map.class)));

        if (Boolean.getBoolean("snapshot.write")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
            fail("snapshot written");
        }
        assertThat(actual).isEqualTo(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
    }

    /** Importer paths and every component schema, keys sorted at every level. */
    private static Object importerContract(Map<?, ?> docs) {
        Map<String, Object> paths = new TreeMap<>();
        ((Map<?, ?>) docs.get("paths")).forEach((path, item) -> {
            if (((String) path).startsWith("/api/import-sessions")) {
                paths.put((String) path, sorted(item));
            }
        });
        Map<String, Object> contract = new TreeMap<>();
        contract.put("paths", paths);
        contract.put("schemas", sorted(((Map<?, ?>) docs.get("components")).get("schemas")));
        return contract;
    }

    private static Object sorted(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), sorted(value)));
            return result;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(ApiContractSnapshotTest::sorted).toList();
        }
        return node;
    }
}
