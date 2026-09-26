package com.universalimporter.api.importsession;

import com.universalimporter.application.importsession.ImportSessionService;
import com.universalimporter.application.importsession.SessionDetails;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportSessionController.class)
class ImportSessionControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");
    private static final ImportSession SESSION =
            ImportSession.create(ID, new SourceFile("customers.csv", SourceFileType.CSV, 7), T0);
    /** A session that has no configuration yet, as after upload. */
    private static final SessionDetails DETAILS = SessionDetails.of(SESSION, ImportConfiguration.empty(ID));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ImportSessionService service;

    @Test
    void upload_passes_the_file_to_the_service_and_answers_201_with_location() throws Exception {
        when(service.upload(eq("customers.csv"), any())).thenReturn(DETAILS);

        mockMvc.perform(multipart("/api/import-sessions")
                        .file(new MockMultipartFile("file", "customers.csv", "text/csv", bytes("a,b\n1,2"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/import-sessions/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.originalFileName").value("customers.csv"))
                .andExpect(jsonPath("$.fileType").value("CSV"))
                .andExpect(jsonPath("$.sizeBytes").value(7))
                .andExpect(jsonPath("$.createdAt").value("2026-09-25T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-25T10:00:00Z"))
                .andExpect(jsonPath("$.config.schema.fields").isArray())
                .andExpect(jsonPath("$.config.schema.fields").isEmpty())
                .andExpect(jsonPath("$.readiness.ready").value(false))
                .andExpect(jsonPath("$.readiness.issues[0].field").value(nullValue()))
                .andExpect(jsonPath("$.readiness.issues[0].code").value("SCHEMA_EMPTY"))
                .andExpect(jsonPath("$.readiness.issues[0].message").value("Target schema has no fields."));

        ArgumentCaptor<InputStreamSource> content = ArgumentCaptor.forClass(InputStreamSource.class);
        verify(service).upload(eq("customers.csv"), content.capture());
        try (InputStream in = content.getValue().getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes("a,b\n1,2"));
        }
    }

    @Test
    void upload_without_a_file_part_is_request_invalid() throws Exception {
        mockMvc.perform(multipart("/api/import-sessions")
                        .file(new MockMultipartFile("other", "customers.csv", "text/csv", bytes("a"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void get_returns_the_session_with_its_configuration_and_readiness() throws Exception {
        when(service.details(ID)).thenReturn(DETAILS);

        mockMvc.perform(get("/api/import-sessions/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.fileType").value("CSV"))
                .andExpect(jsonPath("$.sizeBytes").value(7))
                .andExpect(jsonPath("$.config.schema.fields").isArray())
                .andExpect(jsonPath("$.config.mapping.mappings").isArray())
                .andExpect(jsonPath("$.config.mapping.mappings").isEmpty())
                .andExpect(jsonPath("$.config.transformations.transformations").isArray())
                .andExpect(jsonPath("$.config.transformations.transformations").isEmpty())
                .andExpect(jsonPath("$.config.validations.validations").isArray())
                .andExpect(jsonPath("$.config.validations.validations").isEmpty())
                .andExpect(jsonPath("$.readiness.ready").value(false))
                .andExpect(jsonPath("$.readiness.issues[0].code").value("SCHEMA_EMPTY"));
    }

    @Test
    void get_of_an_unknown_session_is_404_session_not_found() throws Exception {
        when(service.details(ID)).thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));

        mockMvc.perform(get("/api/import-sessions/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void get_with_an_id_that_is_not_a_uuid_is_request_invalid() throws Exception {
        mockMvc.perform(get("/api/import-sessions/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
