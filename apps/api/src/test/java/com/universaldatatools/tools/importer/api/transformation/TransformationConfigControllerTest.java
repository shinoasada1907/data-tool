package com.universaldatatools.tools.importer.api.transformation;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.platform.web.StrictJsonConfig;
import com.universaldatatools.tools.importer.application.configuration.ConfigUpdateResult;
import com.universaldatatools.tools.importer.application.configuration.ConfigurationService;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.Readiness;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.core.transform.TransformationStep;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransformationConfigController.class)
@Import(StrictJsonConfig.class)
class TransformationConfigControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T10:00:00Z");
    private static final String TRIM_NAME = "{\"transformations\":[{\"targetField\":\"name\",\"order\":0,\"type\":\"trim\"}]}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfigurationService service;

    @Test
    void a_valid_configuration_answers_200_with_the_session() throws Exception {
        when(service.updateTransformations(eq(ID), any())).thenReturn(nameTrimmed());

        putTransformations(TRIM_NAME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.id").value(ID.toString()))
                .andExpect(jsonPath("$.session.config.transformations.transformations[0].targetField").value("name"))
                .andExpect(jsonPath("$.session.config.transformations.transformations[0].type").value("trim"))
                .andExpect(jsonPath("$.session.config.transformations.transformations[0].order").value(0))
                .andExpect(jsonPath("$.session.config.transformations.transformations[0].params").isMap())
                .andExpect(jsonPath("$.warnings.length()").value(0));

        verify(service).updateTransformations(ID,
                new TransformationConfig(List.of(new TransformationStep("name", 0, "trim", Map.of()))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"transformations\":[{\"targetField\":\"name\",\"order\":0,\"type\":\"trim\",\"params\":{}}]}",
            "{\"transformations\":[{\"targetField\":\"name\",\"order\":0,\"type\":\"trim\",\"params\":null}]}",
            "{\"transformations\":[]}"})
    void empty_params_and_an_empty_list_are_accepted(String body) throws Exception {
        when(service.updateTransformations(eq(ID), any())).thenReturn(nameTrimmed());

        putTransformations(body).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"transformations\":[null]}",
            "{\"transformations\":[{\"targetField\":\"name\",\"order\":\"abc\",\"type\":\"trim\"}]}",
            "{\"transformations\":[{\"targetField\":\"dob\",\"order\":0,\"type\":\"dateFormat\",\"params\":{\"inputFormat\":5}}]}",
            "not json"})
    void a_malformed_body_is_request_invalid(String body) throws Exception {
        putTransformations(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void configuration_problems_are_422_config_invalid() throws Exception {
        when(service.updateTransformations(eq(ID), any())).thenThrow(new DomainException(ErrorCode.CONFIG_INVALID,
                "Transformation configuration is invalid.",
                List.of(new ProblemItem("phone", "CONFIG_INVALID", "Target field does not exist."))));

        putTransformations(TRIM_NAME)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONFIG_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    void an_unknown_session_is_404() throws Exception {
        when(service.updateTransformations(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));

        putTransformations(TRIM_NAME)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void a_session_in_the_wrong_state_is_409() throws Exception {
        when(service.updateTransformations(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_STATE_INVALID, "Session has failed and cannot be changed."));

        putTransformations(TRIM_NAME)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    private ResultActions putTransformations(String body) throws Exception {
        return mockMvc.perform(put("/api/import-sessions/{id}/transformations", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ConfigUpdateResult nameTrimmed() {
        ImportSession session = ImportSession.restore(ID, new SourceFile("customers.csv", SourceFileType.CSV, 20),
                SessionStatus.CONFIGURING, T0, T0, 1L, null);
        TargetSchema schema = TargetSchema.define(List.of(new FieldSpec("name", "string", false, 0)));
        TransformationConfig transformations =
                new TransformationConfig(List.of(new TransformationStep("name", 0, "trim", null)));
        return new ConfigUpdateResult(session,
                new ImportConfiguration(ID, schema, MappingConfig.empty(), transformations, ValidationConfig.empty(), 1L),
                new Readiness(true, List.of()), List.of());
    }
}
