package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.TextEncoding;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** tool-02 task 1: the shared source of every tool request. */
class SourceDtoTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void options_are_read_in_any_case() {
        SourceRef ref = new SourceDto(ID, new SourceDto.Options("S", "semicolon", "windows-1258", false))
                .toRef("source");

        assertThat(ref.datasetId()).isEqualTo(ID);
        assertThat(ref.options().sheet()).isEqualTo("S");
        assertThat(ref.options().delimiter()).isEqualTo(Delimiter.SEMICOLON);
        assertThat(ref.options().encoding()).isEqualTo(TextEncoding.WINDOWS_1258);
        assertThat(ref.options().hasHeader()).isFalse();
    }

    @Test
    void no_options_means_everything_detected() {
        assertThat(new SourceDto(ID, null).toRef("source").options()).isEqualTo(ReadOptions.defaults());
    }

    @Test
    void mistakes_name_their_place_in_the_body() {
        assertThatThrownBy(() -> new SourceDto(ID, new SourceDto.Options(null, "COLON", null, null)).toRef("old"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.CONFIG_INVALID);
                    assertThat(e.items()).extracting(ProblemItem::message).singleElement().asString()
                            .startsWith("old.options.delimiter must be one of");
                });
        assertThatThrownBy(() -> new SourceDto(null, null).toRef("source"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_INVALID));
    }
}
