package com.universalimporter.api.common;

import com.jayway.jsonpath.JsonPath;
import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.HttpTestClient.Response;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the live API documentation working as endpoints are added (spec: api-docs). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiDocsIntegrationTest {

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void openapi_document_lists_every_endpoint() {
        Response docs = http.get("/v3/api-docs");

        assertThat(docs.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(docs.body(), "$.info.title")).isEqualTo("Universal Importer API");
        Map<String, Object> paths = JsonPath.read(docs.body(), "$.paths");
        assertThat(paths).containsKeys(
                "/api/import-sessions",
                "/api/import-sessions/{id}",
                "/api/import-sessions/{id}/preview",
                "/api/import-sessions/{id}/schema",
                "/api/import-sessions/{id}/mapping");
    }

    @Test
    void upload_is_documented_as_a_multipart_file_so_it_can_be_tried_in_the_browser() {
        Response docs = http.get("/v3/api-docs");

        Map<String, Object> file = JsonPath.read(docs.body(),
                "$.paths['/api/import-sessions'].post.requestBody.content['multipart/form-data'].schema.properties.file");
        assertThat(file).containsEntry("type", "string").containsEntry("format", "binary");
    }

    @Test
    void swagger_ui_page_is_served() {
        Response entry = http.get("/swagger-ui.html");

        assertThat(entry.status()).isEqualTo(302);
        String location = entry.headers().getLocation().toString();
        assertThat(location).endsWith("/swagger-ui/index.html");

        Response page = http.get(location);

        assertThat(page.status()).isEqualTo(200);
        assertThat(page.body()).contains("Swagger UI");
    }
}
