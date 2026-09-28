package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.tools.importer.application.importsession.SourcePreview;
import com.universaldatatools.tools.importer.application.importsession.SourcePreviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SourcePreviewController.class)
class SourcePreviewControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final List<Column> COLUMNS = List.of(new Column(0, "name"), new Column(1, "email"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SourcePreviewService service;

    @Test
    void preview_uses_limit_50_by_default_and_answers_the_contract_shape() throws Exception {
        // Stubbed for limit 50 only: any other limit returns null and fails the request.
        when(service.preview(ID, 50)).thenReturn(new SourcePreview(ID, DataFormat.CSV, null, COLUMNS,
                List.of(new Row(2, List.of("An", "an@x.com"))), 50, 2));

        mockMvc.perform(get("/api/import-sessions/{id}/preview", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(ID.toString()))
                .andExpect(jsonPath("$.fileType").value("CSV"))
                .andExpect(jsonPath("$.sheetName").isEmpty())
                .andExpect(jsonPath("$.columns[1].index").value(1))
                .andExpect(jsonPath("$.columns[1].name").value("email"))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.rows[0].values[0]").value("An"))
                .andExpect(jsonPath("$.previewLimit").value(50))
                .andExpect(jsonPath("$.totalRows").value(2));
    }

    @Test
    void null_cells_keep_their_position_in_values() throws Exception {
        when(service.preview(ID, 50)).thenReturn(new SourcePreview(ID, DataFormat.CSV, null, COLUMNS,
                List.of(new Row(2, Arrays.asList("x", null))), 50, 1));

        mockMvc.perform(get("/api/import-sessions/{id}/preview", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].values.length()").value(2))
                .andExpect(jsonPath("$.rows[0].values[1]").isEmpty());
    }

    @ParameterizedTest(name = "limit={0}")
    @ValueSource(strings = {"0", "201", "abc"})
    void a_limit_outside_1_to_200_is_request_invalid(String limit) throws Exception {
        mockMvc.perform(get("/api/import-sessions/{id}/preview", ID).param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void a_session_that_was_never_inspected_is_409() throws Exception {
        when(service.preview(ID, 50)).thenThrow(
                new DomainException(ErrorCode.SESSION_STATE_INVALID, "Source file has not been inspected."));

        mockMvc.perform(get("/api/import-sessions/{id}/preview", ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    @Test
    void an_unknown_session_is_404() throws Exception {
        when(service.preview(ID, 50)).thenThrow(
                new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));

        mockMvc.perform(get("/api/import-sessions/{id}/preview", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
