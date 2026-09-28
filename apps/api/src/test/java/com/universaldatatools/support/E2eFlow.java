package com.universaldatatools.support;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.HttpTestClient.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BE-F11 end-to-end data and configuration (tasks 6–8): {@code fixtures/e2e/customers.csv}, or the same rows
 * as real XLSX cells ({@link XlsxFixtures#e2eCustomers}), and the configuration sent for them.
 */
public final class E2eFlow {

    public static final String SCHEMA = """
            {"fields":[{"name":"name","type":"string","required":true,"order":0},
                       {"name":"email","type":"email","required":true,"order":1},
                       {"name":"dob","type":"date","required":false,"order":2},
                       {"name":"active","type":"boolean","required":false,"order":3},
                       {"name":"score","type":"number","required":false,"order":4},
                       {"name":"country","type":"string","required":false,"order":5}]}""";

    public static final String MAPPING = """
            {"mappings":[{"targetField":"name","mappingType":"SOURCE_COLUMN","sourceColumn":"Name","constantValue":null},
                         {"targetField":"email","mappingType":"SOURCE_COLUMN","sourceColumn":"Email","constantValue":null},
                         {"targetField":"dob","mappingType":"SOURCE_COLUMN","sourceColumn":"Birth Date","constantValue":null},
                         {"targetField":"active","mappingType":"SOURCE_COLUMN","sourceColumn":"Active","constantValue":null},
                         {"targetField":"score","mappingType":"SOURCE_COLUMN","sourceColumn":"Score","constantValue":null},
                         {"targetField":"country","mappingType":"CONSTANT","sourceColumn":null,"constantValue":"VN"}]}""";

    public static final String VALIDATIONS = """
            {"validations":[{"targetField":"email","type":"unique"}]}""";

    public static final String CSV_TRANSFORMATIONS = """
            {"transformations":[{"targetField":"name","order":0,"type":"trim"},
                                {"targetField":"email","order":0,"type":"trim"},
                                {"targetField":"email","order":1,"type":"lowercase"},
                                {"targetField":"dob","order":0,"type":"dateFormat",
                                 "params":{"inputFormat":"dd/MM/yyyy","outputFormat":"yyyy-MM-dd"}}]}""";

    /** Date cells of an XLSX already come as ISO dates (D9): no dateFormat step. */
    public static final String XLSX_TRANSFORMATIONS = """
            {"transformations":[{"targetField":"name","order":0,"type":"trim"},
                                {"targetField":"email","order":0,"type":"trim"},
                                {"targetField":"email","order":1,"type":"lowercase"}]}""";

    public static final String VALID_ROW_2 =
            "{\"name\":\"An Nguyen\",\"email\":\"an@example.com\",\"dob\":\"1990-12-25\",\"active\":true,\"score\":10,"
                    + "\"country\":\"VN\"}";

    private E2eFlow() {
    }

    public static byte[] customersCsv() {
        try (InputStream in = E2eFlow.class.getResourceAsStream("/fixtures/e2e/customers.csv")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uploads the CSV and returns the new session's path, {@code /api/import-sessions/{id}}. */
    public static String upload(HttpTestClient http) {
        Response upload = http.upload("customers.csv", customersCsv());
        assertThat(upload.status()).isEqualTo(201);
        return "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");
    }

    /** Schema and mapping: the session is READY. */
    public static void configureReady(HttpTestClient http, String session) {
        put(http, session + "/schema", SCHEMA);
        put(http, session + "/mapping", MAPPING);
    }

    /** The whole CSV configuration. */
    public static void configureCsv(HttpTestClient http, String session) {
        configureReady(http, session);
        put(http, session + "/transformations", CSV_TRANSFORMATIONS);
        put(http, session + "/validations", VALIDATIONS);
    }

    public static Response put(HttpTestClient http, String path, String json) {
        Response response = http.putJson(path, json);
        assertThat(response.status()).as("PUT %s: %s", path, response.body()).isEqualTo(200);
        return response;
    }
}
