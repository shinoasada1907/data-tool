package com.universalimporter.api.mapping;

import com.universalimporter.api.common.StrictJsonConfig;
import com.universalimporter.application.configuration.ConfigUpdateResult;
import com.universalimporter.application.configuration.ConfigurationService;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.Readiness;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.mapping.FieldMapping;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.mapping.MappingSpec;
import com.universalimporter.domain.mapping.MappingType;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.transformation.TransformationConfig;
import com.universalimporter.domain.validation.ValidationConfig;
import org.junit.jupiter.api.Test;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingController.class)
@Import(StrictJsonConfig.class)
class MappingControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T10:00:00Z");
    private static final String NAME_MAPPING = "{\"mappings\":[{\"targetField\":\"name\",\"mappingType\":\"SOURCE_COLUMN\","
            + "\"sourceColumn\":\"Họ tên\",\"constantValue\":null}]}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfigurationService service;

    @Test
    void a_valid_mapping_answers_200_with_the_session_and_warnings() throws Exception {
        when(service.updateMapping(eq(ID), any())).thenReturn(nameMappedNoteUnmapped());

        putMapping(NAME_MAPPING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("READY"))
                .andExpect(jsonPath("$.session.config.schema.fields[0].name").value("name"))
                .andExpect(jsonPath("$.session.config.mapping.mappings[0].targetField").value("name"))
                .andExpect(jsonPath("$.session.config.mapping.mappings[0].mappingType").value("SOURCE_COLUMN"))
                .andExpect(jsonPath("$.session.config.mapping.mappings[0].sourceColumn").value("Họ tên"))
                .andExpect(jsonPath("$.session.config.mapping.mappings[0].constantValue").value(nullValue()))
                .andExpect(jsonPath("$.warnings[0].field").value("note"))
                .andExpect(jsonPath("$.warnings[0].code").value("TARGET_FIELD_UNMAPPED"));

        verify(service).updateMapping(ID, List.of(new MappingSpec("name", "SOURCE_COLUMN", "Họ tên", null)));
    }

    @Test
    void a_body_without_mappings_is_request_invalid() throws Exception {
        putMapping("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void a_null_mapping_is_request_invalid() throws Exception {
        putMapping("{\"mappings\":[null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void a_value_of_another_json_type_is_request_invalid() throws Exception {
        putMapping("{\"mappings\":[{\"targetField\":\"country\",\"mappingType\":\"CONSTANT\",\"constantValue\":84}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void an_unknown_mapping_type_reaches_the_domain_rules() throws Exception {
        when(service.updateMapping(eq(ID), any())).thenReturn(nameMappedNoteUnmapped());

        putMapping("{\"mappings\":[{\"targetField\":\"name\",\"mappingType\":\"source_column\",\"sourceColumn\":\"a\"}]}")
                .andExpect(status().isOk());

        verify(service).updateMapping(ID, List.of(new MappingSpec("name", "source_column", "a", null)));
    }

    @Test
    void a_missing_source_column_is_422_with_the_target_field() throws Exception {
        when(service.updateMapping(eq(ID), any())).thenThrow(new DomainException(ErrorCode.SOURCE_COLUMN_NOT_FOUND,
                "Source column not found.",
                List.of(new ProblemItem("name", "SOURCE_COLUMN_NOT_FOUND", "Source column does not exist."))));

        putMapping(NAME_MAPPING)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SOURCE_COLUMN_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void an_unknown_session_is_404() throws Exception {
        when(service.updateMapping(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));

        putMapping(NAME_MAPPING)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void a_session_in_the_wrong_state_is_409() throws Exception {
        when(service.updateMapping(eq(ID), any()))
                .thenThrow(new DomainException(ErrorCode.SESSION_STATE_INVALID, "Session has failed and cannot be changed."));

        putMapping(NAME_MAPPING)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    private ResultActions putMapping(String body) throws Exception {
        return mockMvc.perform(put("/api/import-sessions/{id}/mapping", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ConfigUpdateResult nameMappedNoteUnmapped() {
        ImportSession session = ImportSession.restore(ID, new SourceFile("customers.csv", SourceFileType.CSV, 20),
                SessionStatus.READY, T0, T0, 2L, null);
        TargetSchema schema = TargetSchema.define(List.of(
                new FieldSpec("name", "string", true, 0), new FieldSpec("note", "string", false, 1)));
        MappingConfig mapping = new MappingConfig(List.of(
                new FieldMapping("name", MappingType.SOURCE_COLUMN, "Họ tên", null)));
        return new ConfigUpdateResult(session, new ImportConfiguration(ID, schema, mapping, TransformationConfig.empty(), ValidationConfig.empty(), 1L),
                new Readiness(true, List.of()),
                List.of(new ProblemItem("note", "TARGET_FIELD_UNMAPPED", "Field is not mapped.")));
    }
}
