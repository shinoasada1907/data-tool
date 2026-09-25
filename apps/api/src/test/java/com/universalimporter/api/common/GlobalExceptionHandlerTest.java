package com.universalimporter.api.common;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The probe is registered with @Import: component scanning does not pick up this nested test controller,
// and `controllers` keeps @WebMvcTest from loading the application's real controllers.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorProbeController.class)
@Import(GlobalExceptionHandlerTest.ErrorProbeController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void domain_error_becomes_problem_detail_with_code_and_mapped_status() throws Exception {
        mockMvc.perform(get("/test-errors/domain"))
                .andExpect(status().is(415))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("FILE_UNSUPPORTED"))
                .andExpect(jsonPath("$.detail").value("Only .csv and .xlsx files are supported."))
                .andExpect(jsonPath("$.instance").value("/test-errors/domain"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void domain_error_items_are_listed_under_errors() throws Exception {
        mockMvc.perform(get("/test-errors/items"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("SCHEMA_INVALID"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("SCHEMA_INVALID"))
                .andExpect(jsonPath("$.errors[0].message").value("Duplicate field name"));
    }

    @Test
    void unexpected_exception_is_internal_error_without_leaking_details() throws Exception {
        String body = mockMvc.perform(get("/test-errors/boom"))
                .andExpect(status().is(500))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-db-password").doesNotContain("IllegalStateException");
    }

    @Test
    void malformed_uuid_path_variable_is_request_invalid() throws Exception {
        mockMvc.perform(get("/test-errors/uuid/abc"))
                .andExpect(status().is(400))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.instance").value("/test-errors/uuid/abc"));
    }

    @Test
    void unsupported_method_keeps_405_with_request_invalid_code() throws Exception {
        mockMvc.perform(delete("/test-errors/domain"))
                .andExpect(status().is(405))
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void unknown_endpoint_keeps_404_with_request_invalid_code() throws Exception {
        mockMvc.perform(get("/test-errors/khong-ton-tai"))
                .andExpect(status().is(404))
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @RestController
    static class ErrorProbeController {

        @GetMapping("/test-errors/domain")
        void domain() {
            throw new DomainException(ErrorCode.FILE_UNSUPPORTED, "Only .csv and .xlsx files are supported.");
        }

        @GetMapping("/test-errors/items")
        void items() {
            throw new DomainException(ErrorCode.SCHEMA_INVALID, "Schema is invalid.",
                    List.of(new ProblemItem("email", "SCHEMA_INVALID", "Duplicate field name")));
        }

        @GetMapping("/test-errors/boom")
        void boom() {
            throw new IllegalStateException("secret-db-password");
        }

        @GetMapping("/test-errors/uuid/{id}")
        String uuid(@PathVariable UUID id) {
            return id.toString();
        }
    }
}
