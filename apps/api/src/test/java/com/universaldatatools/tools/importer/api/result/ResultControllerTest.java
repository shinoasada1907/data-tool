package com.universaldatatools.tools.importer.api.result;

import com.universaldatatools.platform.web.PlainNumberJsonConfig;
import com.universaldatatools.platform.web.StrictJsonConfig;
import com.universaldatatools.tools.importer.application.result.ResultPage;
import com.universaldatatools.tools.importer.application.result.ResultQuery;
import com.universaldatatools.tools.importer.application.result.ResultQueryService;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.pipeline.ErrorStage;
import com.universaldatatools.tools.importer.domain.pipeline.ImportError;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;
import com.universaldatatools.tools.importer.domain.pipeline.ResultView;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResultController.class)
@Import({StrictJsonConfig.class, PlainNumberJsonConfig.class})
class ResultControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ResultQueryService service;

    @BeforeEach
    void oneInvalidRow() {
        stubRow(values("email", "abc", "score", new BigDecimal("0.0000001")));
    }

    @Test
    void no_parameters_means_the_first_page_of_valid_rows() throws Exception {
        result("").andExpect(status().isOk());

        verify(service).query(ID, new ResultQuery(ResultView.VALID, 0, 50, null, null));
    }

    @Test
    void every_parameter_reaches_the_service() throws Exception {
        result("?view=INVALID&page=2&size=10&field=email&code=VALIDATION_EMAIL").andExpect(status().isOk());

        verify(service).query(ID, new ResultQuery(ResultView.INVALID, 2, 10, "email", "VALIDATION_EMAIL"));
    }

    @Test
    void blank_filters_mean_no_filter() throws Exception {
        mockMvc.perform(get("/api/import-sessions/" + ID + "/result").param("view", "invalid").param("field", "")
                .param("code", " ")).andExpect(status().isOk());

        verify(service).query(ID, new ResultQuery(ResultView.INVALID, 0, 50, null, null));
    }

    @Test
    void a_size_of_zero_is_refused_before_the_service() throws Exception {
        assertInvalid("?size=0");
        verify(service, never()).query(any(), any());
    }

    @Test
    void a_size_over_200_is_refused() throws Exception {
        assertInvalid("?size=201");
    }

    @Test
    void a_negative_page_is_refused() throws Exception {
        assertInvalid("?page=-1");
    }

    @Test
    void a_page_that_is_not_a_number_is_refused() throws Exception {
        assertInvalid("?page=x");
    }

    @Test
    void an_unknown_view_is_refused() throws Exception {
        assertInvalid("?view=all");
    }

    @Test
    void the_page_answers_with_summary_page_and_rows() throws Exception {
        result("?view=invalid")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view").value("invalid"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(3))
                .andExpect(jsonPath("$.rows[0].valid").value(false))
                .andExpect(jsonPath("$.rows[0].values.email").value("abc"))
                .andExpect(jsonPath("$.rows[0].errors[0].rowNumber").value(3))
                .andExpect(jsonPath("$.rows[0].errors[0].fieldName").value("email"))
                .andExpect(jsonPath("$.rows[0].errors[0].stage").value("VALIDATION"))
                .andExpect(jsonPath("$.rows[0].errors[0].rule").value("email"))
                .andExpect(jsonPath("$.rows[0].errors[0].step").value(nullValue()))
                .andExpect(jsonPath("$.rows[0].errors[0].code").value("VALIDATION_EMAIL"))
                .andExpect(jsonPath("$.rows[0].errors[0].message").value("Value is not a valid email address."))
                .andExpect(jsonPath("$.rows[0].errors[0].sourceValue").value(" ABC "))
                .andExpect(jsonPath("$.summary.sessionId").value(ID.toString()))
                .andExpect(jsonPath("$.summary.status").value("PROCESSED"))
                .andExpect(jsonPath("$.summary.total").value(5))
                .andExpect(jsonPath("$.summary.errorCountsByCode.VALIDATION_EMAIL").value(1))
                .andExpect(jsonPath("$.summary.processedAt").value("2026-09-27T09:00:00Z"));
    }

    @Test
    void small_numbers_are_written_plain() throws Exception {
        String body = result("?view=invalid").andReturn().getResponse().getContentAsString();

        assertThat(body).contains("0.0000001").doesNotContain("1E-7");
        assertThat(body).contains("\"step\":null");
    }

    @Test
    void values_keep_the_schema_order_and_empty_values_stay_null() throws Exception {
        stubRow(values("name", "An", "email", "abc", "note", null, "score", "x"));

        String body = result("?view=invalid").andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"values\":{\"name\":\"An\",\"email\":\"abc\",\"note\":null,\"score\":\"x\"}");
    }

    @Test
    void no_result_is_409() throws Exception {
        when(service.query(eq(ID), any())).thenThrow(new DomainException(ErrorCode.RESULT_NOT_AVAILABLE, "none"));

        result("").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESULT_NOT_AVAILABLE"));
    }

    @Test
    void an_unknown_session_is_404() throws Exception {
        when(service.query(eq(ID), any())).thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "none"));

        result("").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    private void stubRow(Map<String, Object> values) {
        Map<String, Long> byCode = new LinkedHashMap<>();
        byCode.put("VALIDATION_EMAIL", 1L);
        Map<String, Long> byField = new LinkedHashMap<>();
        byField.put("email", 1L);
        ResultSummary summary = new ResultSummary(5, 2, 3, byCode, byField, Instant.parse("2026-09-27T09:00:00Z"), "h");
        RowResult row = new RowResult(3, false, values, List.of(new ImportError(3, "email", ErrorStage.VALIDATION,
                "email", null, RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address.", " ABC ")));
        when(service.query(eq(ID), any())).thenAnswer(call -> {
            ResultQuery query = call.getArgument(1);
            return new ResultPage(summary, SessionStatus.PROCESSED, query.view(), query.page(), query.size(), 3, 1,
                    List.of(row));
        });
    }

    private void assertInvalid(String query) throws Exception {
        result(query).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    private ResultActions result(String query) throws Exception {
        return mockMvc.perform(get("/api/import-sessions/" + ID + "/result" + query));
    }

    private static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            values.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return values;
    }
}
