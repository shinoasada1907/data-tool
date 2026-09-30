package com.universaldatatools.platform.web;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** core-03 task 1: a pointer shows only when there is one, so the importer's errors stay byte for byte. */
class ProblemItemPointerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void the_old_constructor_has_no_pointer() {
        assertThat(new ProblemItem("a", "X", "m").pointer()).isNull();
    }

    @Test
    void a_pointer_is_written_next_to_a_null_field() {
        assertThat(errorsJson(new ProblemItem(null, "X", "m", "/fields/0/name")))
                .isEqualTo("[{\"field\":null,\"code\":\"X\",\"message\":\"m\",\"pointer\":\"/fields/0/name\"}]");
    }

    @Test
    void no_pointer_no_key() {
        assertThat(errorsJson(new ProblemItem("email", "SCHEMA_INVALID", "Duplicate field name.")))
                .isEqualTo("[{\"field\":\"email\",\"code\":\"SCHEMA_INVALID\",\"message\":\"Duplicate field name.\"}]");
    }

    private String errorsJson(ProblemItem item) {
        DomainException ex = new DomainException(ErrorCode.SCHEMA_INVALID, "Bad.", List.of(item));
        Object errors = handler.handleDomain(ex, new MockHttpServletRequest()).getBody().getProperties().get("errors");
        return json.writeValueAsString(errors);
    }
}
