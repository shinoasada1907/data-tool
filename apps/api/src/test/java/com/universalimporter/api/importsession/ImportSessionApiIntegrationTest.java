package com.universalimporter.api.importsession;

import com.jayway.jsonpath.JsonPath;
import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.HttpTestClient.Response;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ImportSessionApiIntegrationTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void uploaded_csv_is_stored_under_its_session_id_and_can_be_read_back() {
        byte[] csv = "name,email\nAn,an@x.com\n".getBytes(UTF_8);

        Response upload = http.upload("customers.csv", csv);

        assertThat(upload.status()).isEqualTo(201);
        String id = JsonPath.read(upload.body(), "$.id");
        assertThat(upload.headers().getLocation()).hasToString("/api/import-sessions/" + id);
        assertThat(storageDir.resolve(id).resolve("source.bin")).hasBinaryContent(csv);

        Response read = http.get("/api/import-sessions/" + id);

        assertThat(read.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(read.body(), "$.id")).isEqualTo(id);
        // Since BE-F02 the upload reads the CSV in the same request, so the session is already CONFIGURING.
        assertThat((String) JsonPath.read(read.body(), "$.status")).isEqualTo("CONFIGURING");
        assertThat((String) JsonPath.read(read.body(), "$.originalFileName")).isEqualTo("customers.csv");
    }

    @Test
    void vietnamese_file_name_sent_as_utf8_survives_the_round_trip() {
        // Browsers send the multipart file name as UTF-8; the server must decode it as UTF-8 too.
        Response upload = http.upload("khách hàng.csv", "a,b\n".getBytes(UTF_8));

        assertThat(upload.status()).isEqualTo(201);
        assertThat((String) JsonPath.read(upload.body(), "$.originalFileName")).isEqualTo("khách hàng.csv");
    }

    @Test
    void uploaded_xlsx_is_recognised_by_its_zip_signature() throws IOException {
        Response upload = http.upload("customers.xlsx", minimalXlsx());

        assertThat(upload.status()).isEqualTo(201);
        assertThat((String) JsonPath.read(upload.body(), "$.fileType")).isEqualTo("XLSX");
    }

    @Test
    void xls_is_rejected_with_a_problem_detail() {
        Response upload = http.upload("data.xls", "a,b".getBytes(UTF_8));

        assertThat(upload.status()).isEqualTo(415);
        assertThat(upload.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat((String) JsonPath.read(upload.body(), "$.code")).isEqualTo("FILE_UNSUPPORTED");
    }

    private static byte[] minimalXlsx() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write("<workbook/>".getBytes(UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
