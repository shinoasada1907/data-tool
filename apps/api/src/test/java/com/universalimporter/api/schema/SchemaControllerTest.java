package com.universalimporter.api.schema;

import com.universalimporter.api.common.StrictJsonConfig;
import com.universalimporter.application.configuration.ConfigUpdateResult;
import com.universalimporter.application.configuration.ConfigurationService;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.Readiness;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchemaController.class)
@Import(StrictJsonConfig.class)
class SchemaControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T10:00:00Z");
    private static final String EMAIL_SCHEMA =
            "{\"fields\":[{\"name\":\"email\",\"type\":\"email\",\"required\":true,\"order\":0}]}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfigurationService service;

    @Test
    void a_valid_schema_answers_200_with_the_session_and_warnings() throws Exception {
        when(service.updateSchema(eq(ID), any())).thenReturn(readyWithEmail());

        putSchema(EMAIL_SCHEMA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.id").value(ID.toString()))
                .andExpect(jsonPath("$.session.status").value("READY"))
                .andExpect(jsonPath("$.session.config.schema.fields[0].name").value("email"))
                .andExpect(jsonPath("$.session.config.schema.fields[0].type").value("email"))
                .andExpect(jsonPath("$.session.config.schema.fields[0].required").value(true))
                .andExpect(jsonPath("$.session.config.schema.fields[0].order").value(0))
                .andExpect(jsonPath("$.session.readiness.ready").value(true))
                .andExpect(jsonPath("$.session.readiness.issues").isEmpty())
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings").isEmpty());

        verify(service).updateSchema(ID, List.of(new FieldSpec("email", "email", true, 0)));
    }

    @Test
    void a_body_without_fields_is_request_invalid() throws Exception {
        putSchema("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void a_null_field_is_request_invalid() throws Exception {
        putSchema("{\"fields\":[null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void a_value_of_the_wrong_json_type_is_request_invalid() throws Exception {
        putSchema("{\"fields\":[{\"name\":\"a\",\"required\":\"yes\",\"order\":0}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    /** Jackson would otherwise quietly convert these; the spec wants a 400 for any wrong JSON type. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"fields\":[{\"name\":\"a\",\"type\":\"string\",\"required\":\"true\",\"order\":0}]}",
            "{\"fields\":[{\"name\":\"a\",\"type\":\"string\",\"required\":1,\"order\":0}]}",
            "{\"fields\":[{\"name\":\"a\",\"type\":\"string\",\"order\":\"1\"}]}",
            "{\"fields\":[{\"name\":\"a\",\"type\":\"string\",\"order\":1.5}]}",
            "{\"fields\":[{\"name\":123,\"type\":\"string\",\"order\":0}]}",
            "{\"fields\":[{\"name\":\"a\",\"type\":true,\"order\":0}]}"})
    void a_scalar_of_another_json_type_is_not_converted_but_request_invalid(String body) throws Exception {
        putSchema(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void a_body_that_is_not_json_is_request_invalid() throws Exception {
        putSchema("fields: email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void schema_problems_are_422_with_one_error_per_problem() throws Exception {
        when(service.updateSchema(eq(ID), any())).thenThrow(new DomainException(ErrorCode.SCHEMA_INVALID,
                "Target schema is invalid.", List.of(new ProblemItem("email", "SCHEMA_INVALID", "Duplicate field name."))));

        putSchema(EMAIL_SCHEMA)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SCHEMA_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("SCHEMA_INVALID"))
                .andExpect(jsonPath("$.errors[0].message").value("Duplicate field name."));
    }

    @Test
    void an_unknown_session_is_404() throws Exception {
        when(service.updateSchema(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));

        putSchema(EMAIL_SCHEMA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void a_session_in_the_wrong_state_is_409() throws Exception {
        when(service.updateSchema(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_STATE_INVALID, "Session has failed and cannot be changed."));

        putSchema(EMAIL_SCHEMA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    @Test
    void a_missing_required_flag_means_optional_and_a_missing_order_reaches_the_domain_rules() throws Exception {
        when(service.updateSchema(eq(ID), any())).thenReturn(readyWithEmail());

        putSchema("{\"fields\":[{\"name\":\"a\",\"type\":\"string\"}]}").andExpect(status().isOk());

        verify(service).updateSchema(ID, List.of(new FieldSpec("a", "string", false, null)));
    }

    private ResultActions putSchema(String body) throws Exception {
        return mockMvc.perform(put("/api/import-sessions/{id}/schema", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ConfigUpdateResult readyWithEmail() {
        ImportSession session = ImportSession.restore(ID, new SourceFile("customers.csv", SourceFileType.CSV, 7),
                SessionStatus.READY, T0, T0, 1L, null);
        TargetSchema schema = TargetSchema.define(List.of(new FieldSpec("email", "email", true, 0)));
        return new ConfigUpdateResult(session, new ImportConfiguration(ID, schema, MappingConfig.empty(), 0L),
                new Readiness(true, List.of()), List.of());
    }
}
