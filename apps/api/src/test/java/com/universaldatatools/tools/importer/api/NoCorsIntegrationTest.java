package com.universaldatatools.tools.importer.api;

import com.universaldatatools.support.E2eFlow;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** No CORS (design D3): the FE goes through the Vite proxy, so the browser never calls another origin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class NoCorsIntegrationTest {

    private static final String VITE_ORIGIN = "http://localhost:5173";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("toolbox.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @Test
    void a_cross_origin_request_gets_no_permission() {
        HttpTestClient http = new HttpTestClient(port);
        String session = E2eFlow.upload(http);

        Response get = http.request(HttpMethod.GET, session, Map.of(HttpHeaders.ORIGIN, VITE_ORIGIN));
        Response preflight = http.request(HttpMethod.OPTIONS, session, Map.of(HttpHeaders.ORIGIN, VITE_ORIGIN,
                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"));

        assertThat(get.status()).isEqualTo(200);
        assertThat(get.headers().containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isFalse();
        assertThat(preflight.headers().containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isFalse();
    }
}
