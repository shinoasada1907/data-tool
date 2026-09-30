package com.universaldatatools.tools.importer.api.export;

import com.universaldatatools.tools.importer.application.export.ExportDownload;
import com.universaldatatools.tools.importer.application.export.ExportService;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.tools.importer.domain.export.ExportFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExportController.class)
class ExportControllerTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final String BASE = "/api/import-sessions/" + ID;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExportService service;

    private final AtomicInteger closed = new AtomicInteger();

    @Test
    void json_downloads_as_an_attachment_written_synchronously() throws Exception {
        when(service.prepareValidRows(ID, ExportFormat.JSON)).thenReturn(download("customers-valid.json",
                "application/json", out -> out.write("[]".getBytes(StandardCharsets.UTF_8))));

        mockMvc.perform(get(BASE + "/export?format=json"))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''customers-valid.json")))
                .andExpect(content().string("[]"));
        assertThat(closed).hasValue(1);
    }

    @Test
    void the_format_is_case_insensitive() throws Exception {
        when(service.prepareValidRows(ID, ExportFormat.CSV)).thenReturn(download("customers-valid.csv",
                "text/csv;charset=UTF-8", out -> { }));

        mockMvc.perform(get(BASE + "/export?format=CSV")).andExpect(status().isOk());

        verify(service).prepareValidRows(ID, ExportFormat.CSV);
    }

    @Test
    void a_missing_format_is_400_before_the_service() throws Exception {
        mockMvc.perform(get(BASE + "/export"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));

        verify(service, never()).prepareValidRows(any(), any());
    }

    @Test
    void an_unknown_format_is_400() throws Exception {
        mockMvc.perform(get(BASE + "/export?format=xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void no_current_result_is_409() throws Exception {
        when(service.prepareValidRows(ID, ExportFormat.JSON))
                .thenThrow(new DomainException(ErrorCode.RESULT_NOT_AVAILABLE, "none"));

        mockMvc.perform(get(BASE + "/export?format=json"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_AVAILABLE"));
    }

    @Test
    void rows_that_cannot_be_opened_are_500_export_failed() throws Exception {
        when(service.prepareValidRows(ID, ExportFormat.JSON))
                .thenThrow(new DomainException(ErrorCode.EXPORT_FAILED, "Export could not be started."));

        mockMvc.perform(get(BASE + "/export?format=json"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EXPORT_FAILED"));
    }

    @Test
    void the_error_report_name_is_percent_encoded() throws Exception {
        when(service.prepareErrorReport(ID)).thenReturn(download("khách hàng-errors.csv", "text/csv;charset=UTF-8",
                out -> out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF})));

        mockMvc.perform(get(BASE + "/errors/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''kh%C3%A1ch%20h%C3%A0ng-errors.csv")));
        assertThat(closed).hasValue(1);
    }

    @Test
    void an_unknown_session_error_report_is_404() throws Exception {
        when(service.prepareErrorReport(ID)).thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND, "none"));

        mockMvc.perform(get(BASE + "/errors/export"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void a_failure_before_anything_is_sent_is_a_clean_500_export_failed() throws Exception {
        when(service.prepareValidRows(ID, ExportFormat.JSON)).thenReturn(download("customers-valid.json",
                "application/json", out -> {
                    out.write("[{\"name\":\"An\"}".getBytes(StandardCharsets.UTF_8));
                    throw new UncheckedIOException(new IOException("disk gone"));
                }));

        MvcResult result = mockMvc.perform(get(BASE + "/export?format=json"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(jsonPath("$.code").value("EXPORT_FAILED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("An");
        assertThat(closed).hasValue(1);
    }

    @Test
    void a_failure_after_the_response_started_propagates_without_an_error_body() {
        byte[] firstPart = "x".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        when(service.prepareValidRows(ID, ExportFormat.JSON)).thenReturn(download("customers-valid.json",
                "application/json", out -> {
                    out.write(firstPart);
                    throw new UncheckedIOException(new IOException("disk gone"));
                }));

        Throwable failure = catchThrowable(() -> mockMvc.perform(get(BASE + "/export?format=json")));

        // Thrown to the container, which drops the connection (checked on real Tomcat in ExportApiIntegrationTest).
        assertThat(failure).isNotNull();
        assertThat(closed).hasValue(1);
    }

    private ExportDownload download(String fileName, String contentType,
                                    com.universaldatatools.tools.importer.application.export.ExportBody body) {
        return new ExportDownload(fileName, contentType, body, closed::incrementAndGet);
    }
}
