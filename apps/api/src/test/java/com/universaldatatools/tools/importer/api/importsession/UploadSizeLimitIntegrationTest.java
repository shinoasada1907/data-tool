package com.universaldatatools.tools.importer.api.importsession;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=2KB"
        })
@Import(TestcontainersConfiguration.class)
class UploadSizeLimitIntegrationTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @Test
    void oversized_upload_receives_a_413_problem_instead_of_a_connection_reset() {
        // Larger than Tomcat's default 2MB swallow limit: without max-swallow-size=-1 the server
        // would stop reading and the client would see a reset connection, not this response.
        byte[] threeMegabytes = new byte[3 * 1024 * 1024];
        Arrays.fill(threeMegabytes, (byte) 'a');

        Response upload = new HttpTestClient(port).upload("big.csv", threeMegabytes);

        assertThat(upload.status()).isEqualTo(413);
        assertThat(upload.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat((String) JsonPath.read(upload.body(), "$.code")).isEqualTo("FILE_TOO_LARGE");
    }
}
