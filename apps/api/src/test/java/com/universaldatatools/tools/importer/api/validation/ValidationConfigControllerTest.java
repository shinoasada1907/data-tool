package com.universaldatatools.tools.importer.api.validation;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationRuleConfig;
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

@WebMvcTest(ValidationConfigController.class)
@Import(StrictJsonConfig.class)
class ValidationConfigControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T10:00:00Z");
    private static final String UNIQUE_EMAIL = "{\"validations\":[{\"targetField\":\"email\",\"type\":\"unique\"}]}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfigurationService service;

    @Test
    void a_valid_configuration_answers_200_with_the_session() throws Exception {
        when(service.updateValidations(eq(ID), any())).thenReturn(result(List.of()));

        putValidations(UNIQUE_EMAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.config.validations.validations[0].targetField").value("email"))
                .andExpect(jsonPath("$.session.config.validations.validations[0].type").value("unique"))
                .andExpect(jsonPath("$.warnings.length()").value(0));

        verify(service).updateValidations(ID,
                new ValidationConfig(List.of(new ValidationRuleConfig("email", "unique", null))));
    }

    @Test
    void ignored_rules_come_back_as_warnings() throws Exception {
        when(service.updateValidations(eq(ID), any())).thenReturn(result(List.of(new ProblemItem("name",
                "RULE_IMPLIED_BY_SCHEMA", "Rule 'required' is derived from the schema and was ignored."))));

        putValidations("{\"validations\":[{\"targetField\":\"name\",\"type\":\"required\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0].code").value("RULE_IMPLIED_BY_SCHEMA"))
                .andExpect(jsonPath("$.warnings[0].field").value("name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"validations\":[{\"targetField\":\"email\",\"type\":\"unique\",\"params\":null}]}",
            "{\"validations\":[{\"targetField\":\"email\",\"type\":\"unique\",\"params\":{}}]}",
            "{\"validations\":[]}"})
    void empty_params_and_an_empty_list_are_accepted(String body) throws Exception {
        when(service.updateValidations(eq(ID), any())).thenReturn(result(List.of()));

        putValidations(body).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"validations\":[null]}", "{\"validations\":[{\"targetField\":1,\"type\":\"unique\"}]}",
            "not json"})
    void a_malformed_body_is_request_invalid(String body) throws Exception {
        putValidations(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void configuration_problems_are_422_config_invalid() throws Exception {
        when(service.updateValidations(eq(ID), any())).thenThrow(new DomainException(ErrorCode.CONFIG_INVALID,
                "Validation configuration is invalid.", List.of(new ProblemItem("age", "CONFIG_INVALID",
                "Rule 'email' only applies to fields of type string."))));

        putValidations(UNIQUE_EMAIL)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONFIG_INVALID"))
                .andExpect(jsonPath("$.errors[0].code").value("CONFIG_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("age"));
    }

    @Test
    void an_unknown_session_is_404() throws Exception {
        when(service.updateValidations(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));

        putValidations(UNIQUE_EMAIL).andExpect(status().isNotFound());
    }

    @Test
    void a_session_in_the_wrong_state_is_409() throws Exception {
        when(service.updateValidations(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_STATE_INVALID, "Session has failed and cannot be changed."));

        putValidations(UNIQUE_EMAIL).andExpect(status().isConflict());
    }

    private ResultActions putValidations(String body) throws Exception {
        return mockMvc.perform(put("/api/import-sessions/{id}/validations", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ConfigUpdateResult result(List<ProblemItem> warnings) {
        ImportSession session = ImportSession.restore(ID, new SourceFile("customers.csv", SourceFileType.CSV, 20),
                SessionStatus.READY, T0, T0, 1L, null);
        TargetSchema schema = TargetSchema.define(List.of(new FieldSpec("email", "email", false, 0)));
        ValidationConfig validations = new ValidationConfig(List.of(new ValidationRuleConfig("email", "unique", null)));
        return new ConfigUpdateResult(session, new ImportConfiguration(ID, schema, MappingConfig.empty(),
                TransformationConfig.empty(), validations, 1L), new Readiness(true, List.of()), warnings);
    }
}
