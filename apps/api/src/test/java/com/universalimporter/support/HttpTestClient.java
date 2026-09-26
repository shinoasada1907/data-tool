package com.universalimporter.support;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Real HTTP calls against a running test server; never throws on 4xx/5xx so tests can assert on them. */
public final class HttpTestClient {

    public record Response(int status, HttpHeaders headers, String body) {
    }

    private final RestClient client;

    public HttpTestClient(int port) {
        this.client = RestClient.create("http://localhost:" + port);
    }

    public Response upload(String fileName, byte[] content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return toResponse(client.post().uri("/api/import-sessions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .toEntity(String.class));
    }

    public Response putJson(String path, String json) {
        return toResponse(client.put().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .toEntity(String.class));
    }

    public Response get(String path) {
        return toResponse(client.get().uri(path)
                .retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .toEntity(String.class));
    }

    private static Response toResponse(ResponseEntity<String> entity) {
        return new Response(entity.getStatusCode().value(), entity.getHeaders(), entity.getBody());
    }
}
