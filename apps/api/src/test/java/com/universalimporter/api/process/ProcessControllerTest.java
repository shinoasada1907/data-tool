package com.universalimporter.api.process;

import com.universalimporter.application.pipeline.PipelineSummaryView;
import com.universalimporter.application.pipeline.ProcessService;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.pipeline.ResultSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessController.class)
class ProcessControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProcessService service;

    @Test
    void a_run_answers_200_with_the_summary() throws Exception {
        Map<String, Long> byCode = new LinkedHashMap<>();
        byCode.put("VALIDATION_TYPE", 2L);
        Map<String, Long> byField = new LinkedHashMap<>();
        byField.put("email", 2L);
        when(service.process(ID)).thenReturn(new PipelineSummaryView(ID, SessionStatus.PROCESSED,
                new ResultSummary(6, 3, 3, byCode, byField, Instant.parse("2026-09-25T10:00:00Z"), "hash")));

        process()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.valid").value(3))
                .andExpect(jsonPath("$.invalid").value(3))
                .andExpect(jsonPath("$.errorCountsByCode.VALIDATION_TYPE").value(2))
                .andExpect(jsonPath("$.errorCountsByField.email").value(2))
                .andExpect(jsonPath("$.processedAt").value("2026-09-25T10:00:00Z"));
    }

    @Test
    void a_session_that_is_not_ready_is_409_with_its_issues() throws Exception {
        when(service.process(ID)).thenThrow(new DomainException(ErrorCode.SESSION_NOT_READY,
                "Session is not ready to process.",
                List.of(new ProblemItem("email", "TARGET_FIELD_REQUIRED", "Required field is not mapped."))));

        process()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_READY"))
                .andExpect(jsonPath("$.errors[0].code").value("TARGET_FIELD_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void a_failed_session_is_409() throws Exception {
        when(service.process(ID)).thenThrow(new DomainException(ErrorCode.SESSION_STATE_INVALID, "failed"));

        process().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    @Test
    void a_broken_file_is_422() throws Exception {
        when(service.process(ID)).thenThrow(new DomainException(ErrorCode.FILE_PARSE_ERROR, "CSV syntax error near row 4."));

        process().andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("FILE_PARSE_ERROR"));
    }

    @Test
    void an_unreadable_file_is_500() throws Exception {
        when(service.process(ID)).thenThrow(new DomainException(ErrorCode.INTERNAL_ERROR, "Source file could not be read."));

        process().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void an_unknown_session_is_404() throws Exception {
        when(service.process(ID)).thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));

        process().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    private ResultActions process() throws Exception {
        return mockMvc.perform(post("/api/import-sessions/{id}/process", ID));
    }
}
