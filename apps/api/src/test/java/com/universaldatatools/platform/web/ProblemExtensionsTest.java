package com.universaldatatools.platform.web;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** core-04 task 2: extensions become top-level members; the retry delay becomes the Retry-After header. */
class ProblemExtensionsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/datasets/x");

    @Test
    void a_retry_delay_is_a_header_not_a_member() {
        ResponseEntity<ProblemDetail> response =
                handler.handleDomain(DomainException.retryLater(ErrorCode.SERVER_BUSY, "Busy.", 5), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(response.getBody().getProperties()).containsEntry("code", "SERVER_BUSY")
                .doesNotContainKey(DomainException.RETRY_AFTER);
    }

    @Test
    void other_extensions_are_top_level_members() {
        DomainException ex = new DomainException(ErrorCode.CONFIG_INVALID, "Bad keys.", List.of(),
                Map.of("keyProblems", List.of("x")));

        ResponseEntity<ProblemDetail> response = handler.handleDomain(ex, request);

        assertThat(response.getBody().getProperties()).containsEntry("keyProblems", List.of("x"));
        assertThat(response.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
    }

    @Test
    void server_busy_always_says_when_to_retry() {
        ResponseEntity<ProblemDetail> response =
                handler.handleDomain(new DomainException(ErrorCode.SERVER_BUSY, "Busy."), request);

        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    @Test
    void dataset_not_found_is_404() {
        assertThat(handler.handleDomain(new DomainException(ErrorCode.DATASET_NOT_FOUND, "Nope."), request)
                .getStatusCode().value()).isEqualTo(404);
    }
}
