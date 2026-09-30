package com.universaldatatools.tools.validator;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Download;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** tool-05 (spec: data-validator), every scenario through real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ValidatorApiIntegrationTest {

    private static final String PEOPLE = "email,age,code\na@x.com,20,A\nb@x.com,30,B\na@,16,C\n";
    private static final String PEOPLE_SCHEMA = """
            {"name":"People","fields":[
              {"name":"email","type":"email","required":true},
              {"name":"age","type":"number","constraints":{"min":18}}]}""";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void a_file_with_an_invalid_row() {
        Response created = run(upload("people.csv", PEOPLE), PEOPLE_SCHEMA);

        assertThat(created.status()).isEqualTo(201);
        String id = JsonPath.read(created.body(), "$.id");
        assertThat(created.headers().getLocation()).hasToString("/api/validator/runs/" + id);
        assertThat(created.headers().getCacheControl()).isEqualTo("no-store");
        assertThat((Object) JsonPath.read(created.body(), "$.summary")).isEqualTo(Map.of(
                "totalRows", 3, "validRows", 2, "invalidRows", 1, "errorCount", 2,
                "errorCountsByCode", Map.of("VALIDATION_EMAIL", 1, "VALIDATION_MIN", 1),
                "errorCountsByField", Map.of("email", 1, "age", 1)));
        assertThat((List<String>) JsonPath.read(created.body(), "$.fields")).containsExactly("email", "age");
        assertThat((List<String>) JsonPath.read(created.body(), "$.compatibility.extraColumns")).containsExactly("code");
        assertThat((String) JsonPath.read(created.body(), "$.sources[0].fileName")).isEqualTo("people.csv");
        assertThat((String) JsonPath.read(created.body(), "$.sources[0].options.delimiter")).isEqualTo("COMMA");
        assertThat((String) JsonPath.read(created.body(), "$.schemaName")).isEqualTo("People");

        Response got = http.get("/api/validator/runs/" + id);
        assertThat(got.status()).isEqualTo(200);
        assertThat(got.headers().getCacheControl()).isEqualTo("no-store");
        // Read back from jsonb, which orders keys its own way: codes sorted, fields in schema order.
        assertThat(((Map<String, Object>) JsonPath.read(got.body(), "$.summary.errorCountsByCode")).keySet())
                .containsExactly("VALIDATION_EMAIL", "VALIDATION_MIN");
        assertThat(((Map<String, Object>) JsonPath.read(got.body(), "$.summary.errorCountsByField")).keySet())
                .containsExactly("email", "age");
    }

    @Test
    void a_repeat_of_an_invalid_row_is_still_a_repeat() {
        String schema = """
                {"name":"S","fields":[{"name":"code","type":"string","constraints":{"unique":true}},
                                     {"name":"n","type":"number"}]}""";
        Response created = run(upload("u.csv", "code,n\nA,x\nA,1\n"), schema);

        String id = JsonPath.read(created.body(), "$.id");
        Response invalid = http.get("/api/validator/runs/" + id + "/rows?view=invalid&code=VALIDATION_UNIQUE");
        assertThat((Integer) JsonPath.read(invalid.body(), "$.page.totalElements")).isEqualTo(1);
        assertThat((String) JsonPath.read(invalid.body(), "$.rows[0].errors[0].message"))
                .isEqualTo("Duplicate value; first seen in row 2.");
        assertThat((Integer) JsonPath.read(invalid.body(), "$.rows[0].rowNumber")).isEqualTo(3);
    }

    @Test
    void a_wrong_schema_points_at_the_wrong_value_before_the_dataset_is_read() {
        String schema = """
                {"name":"S","fields":[{"name":"a","type":"string"},{"name":"b","type":"string","constraints":{"min":1}}]}""";
        Response response = run("00000000-0000-4000-8000-000000000000", schema);

        assertThat(response.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SCHEMA_INVALID");
        assertThat((String) JsonPath.read(response.body(), "$.errors[0].code")).isEqualTo("CONSTRAINT_INVALID");
        assertThat((String) JsonPath.read(response.body(), "$.errors[0].pointer"))
                .isEqualTo("/schema/fields/1/constraints/min");
    }

    @Test
    void a_required_field_without_a_column() {
        long before = runs();
        String schema = """
                {"name":"S","fields":[{"name":"phone","type":"string","required":true},{"name":"Email","type":"email"}]}""";
        Response response = run(upload("p.csv", PEOPLE), schema);

        assertThat(response.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SCHEMA_INCOMPATIBLE");
        assertThat((String) JsonPath.read(response.body(), "$.errors[0].field")).isEqualTo("phone");
        assertThat((String) JsonPath.read(response.body(), "$.errors[0].code")).isEqualTo("FIELD_MISSING");
        assertThat(runs()).isEqualTo(before);
    }

    @Test
    void missing_things() {
        assertThat(run("00000000-0000-4000-8000-000000000000", PEOPLE_SCHEMA).status()).isEqualTo(404);
        assertThat(http.postJson("/api/validator/runs", "{\"schema\":" + PEOPLE_SCHEMA + "}").status()).isEqualTo(400);
        Response unknown = http.get("/api/validator/runs/00000000-0000-4000-8000-000000000000");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(unknown.body(), "$.code")).isEqualTo("RUN_NOT_FOUND");
        assertThat(http.get("/api/validator/runs/not-a-uuid").status()).isEqualTo(400);
    }

    @Test
    void delete_then_gone() {
        String id = JsonPath.read(run(upload("d.csv", PEOPLE), PEOPLE_SCHEMA).body(), "$.id");
        assertThat(http.request(HttpMethod.DELETE, "/api/validator/runs/" + id, Map.of()).status()).isEqualTo(204);
        assertThat(http.get("/api/validator/runs/" + id).status()).isEqualTo(404);
    }

    @Test
    void rows_by_view_with_source_values() {
        String id = JsonPath.read(run(upload("r.csv", PEOPLE), PEOPLE_SCHEMA).body(), "$.id");

        Response valid = http.get("/api/validator/runs/" + id + "/rows");
        assertThat((String) JsonPath.read(valid.body(), "$.view")).isEqualTo("VALID");
        assertThat((List<Object>) JsonPath.read(valid.body(), "$.rows[0].values")).containsExactly("a@x.com", "20");
        assertThat((List<Object>) JsonPath.read(valid.body(), "$.rows[0].errors")).isEmpty();

        Response invalid = http.get("/api/validator/runs/" + id + "/rows?view=invalid");
        assertThat((String) JsonPath.read(invalid.body(), "$.rows[0].errors[0].value")).isEqualTo("a@");
        assertThat((String) JsonPath.read(invalid.body(), "$.rows[0].errors[0].rule")).isEqualTo("email");
        assertThat((String) JsonPath.read(invalid.body(), "$.rows[0].errors[1].value")).isEqualTo("16");

        Response byCode = http.get("/api/validator/runs/" + id + "/rows?view=INVALID&code=VALIDATION_MIN&field=age");
        assertThat((Integer) JsonPath.read(byCode.body(), "$.page.totalElements")).isEqualTo(1);
        Response none = http.get("/api/validator/runs/" + id + "/rows?view=INVALID&code=VALIDATION_MIN&field=email");
        assertThat((Integer) JsonPath.read(none.body(), "$.page.totalElements")).isZero();

        assertThat(http.get("/api/validator/runs/" + id + "/rows?view=x").status()).isEqualTo(400);
        assertThat(http.get("/api/validator/runs/" + id + "/rows?size=0").status()).isEqualTo(400);
    }

    @Test
    void past_the_last_page_and_after_the_dataset_is_gone() {
        StringBuilder csv = new StringBuilder("email\n");
        for (int i = 0; i < 30; i++) {
            csv.append("u").append(i).append("@x.com\n");
        }
        String dataset = upload("many.csv", csv.toString());
        String id = JsonPath.read(run(dataset, "{\"name\":\"S\",\"fields\":[{\"name\":\"email\",\"type\":\"email\"}]}").body(), "$.id");
        assertThat(http.request(HttpMethod.DELETE, "/api/datasets/" + dataset, Map.of()).status()).isEqualTo(204);

        Response page = http.get("/api/validator/runs/" + id + "/rows?page=5&size=10");
        assertThat(page.status()).isEqualTo(200);
        assertThat((List<Object>) JsonPath.read(page.body(), "$.rows")).isEmpty();
        assertThat((Integer) JsonPath.read(page.body(), "$.page.totalElements")).isEqualTo(30);
        assertThat((Integer) JsonPath.read(page.body(), "$.page.totalPages")).isEqualTo(3);
        Response second = http.get("/api/validator/runs/" + id + "/rows?page=1&size=10");
        assertThat((Integer) JsonPath.read(second.body(), "$.rows[0].rowNumber")).isEqualTo(12);
        assertThat(export(id, "VALID", "{\"format\":\"CSV\",\"csv\":{\"bom\":false}}").status()).isEqualTo(200);
    }

    @Test
    void valid_rows_take_the_schema_types() {
        String schema = """
                {"name":"S","fields":[{"name":"dob","type":"date","constraints":{"format":"dd/MM/yyyy"}},
                  {"name":"n","type":"number"},{"name":"ok","type":"boolean"},{"name":"s","type":"string"}]}""";
        String id = JsonPath.read(run(upload("t.csv", "dob,n,ok,s\n31/01/2024,1.50,1,007\n"), schema).body(), "$.id");

        Download file = export(id, "VALID", "{\"format\":\"JSON\"}");

        assertThat(file.status()).isEqualTo(200);
        assertThat(file.text()).isEqualTo("[{\"dob\":\"2024-01-31\",\"n\":1.50,\"ok\":true,\"s\":\"007\"}]");
        assertThat(file.headers().getFirst("Content-Disposition")).contains("filename*=UTF-8''t-valid.json");
    }

    @Test
    void the_error_list_and_the_invalid_rows() {
        String id = JsonPath.read(run(upload("e.csv", PEOPLE), PEOPLE_SCHEMA).body(), "$.id");

        Download errors = export(id, "ERRORS", "{\"format\":\"CSV\",\"csv\":{\"bom\":false}}");
        assertThat(errors.text().split("\r?\n")).containsExactly(
                "row,field,code,rule,message,value",
                "4,email,VALIDATION_EMAIL,email,Value is not a valid email address.,a@",
                "4,age,VALIDATION_MIN,min,Value must be at least 18.,16");

        Download invalid = export(id, "invalid", "{\"format\":\"CSV\",\"csv\":{\"bom\":false}}");
        assertThat(invalid.text().split("\r?\n")).containsExactly(
                "_row,email,age,_errors",
                "4,a@,16,email: Value is not a valid email address.; age: Value must be at least 18.");
        assertThat(invalid.headers().getFirst("Content-Disposition")).contains("e-invalid.csv");
    }

    @Test
    void export_errors_come_first() {
        String id = JsonPath.read(run(upload("x.csv", PEOPLE), PEOPLE_SCHEMA).body(), "$.id");
        assertThat(export(id, "ALL", "{\"format\":\"CSV\"}").status()).isEqualTo(400);
        assertThat(export(id, "VALID", "{\"format\":\"PDF\"}").status()).isEqualTo(422);
        assertThat(export("00000000-0000-4000-8000-000000000000", "VALID", "{\"format\":\"CSV\"}").status())
                .isEqualTo(404);
    }

    private String upload(String name, String csv) {
        Response response = http.uploadTo("/api/datasets", name, csv.getBytes(StandardCharsets.UTF_8));
        assertThat(response.status()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.id");
    }

    private Response run(String datasetId, String schema) {
        return http.postJson("/api/validator/runs",
                "{\"source\":{\"datasetId\":\"" + datasetId + "\"},\"schema\":" + schema + "}");
    }

    private Download export(String id, String content, String output) {
        return http.downloadPost("/api/validator/runs/" + id + "/export",
                "{\"content\":\"" + content + "\",\"output\":" + output + "}");
    }

    private long runs() {
        return jdbc.queryForObject("select count(*) from tool_run", Long.class);
    }
}
