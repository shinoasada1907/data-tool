package com.universaldatatools.core.transform;

import com.universaldatatools.core.schema.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-03 task 8: titleCase, replace, normalizeNull and the catalog. */
class NewTransformationsTest {

    private final TitleCaseTransformation titleCase = new TitleCaseTransformation();
    private final ReplaceTransformation replace = new ReplaceTransformation();
    private final NormalizeNullTransformation normalizeNull = new NormalizeNullTransformation();

    @Test
    void title_case() {
        assertThat(titleCase.transform("nguyễn  VĂN a", ctx())).isEqualTo("Nguyễn  Văn A");
        assertThat(titleCase.transform("o'NEIL", ctx())).isEqualTo("O'neil");
        assertThat(titleCase.transform(null, ctx())).isNull();
        assertThat(titleCase.validate(ctx("x", "1"))).containsExactly("Unknown parameter 'x' for 'titleCase'.");
    }

    @Test
    void replace_a_whole_value() {
        assertThat(replace.transform("N/A", ctx("find", "N/A"))).isEmpty();
        assertThat(replace.transform("N/A2", ctx("find", "N/A"))).isEqualTo("N/A2");
        assertThat(replace.transform("a", ctx("find", "A"))).isEqualTo("a");
        assertThat(replace.transform(null, ctx("find", "A"))).isNull();
    }

    @Test
    void replace_every_occurrence_ignoring_case() {
        TransformationContext hn = ctx("find", "hn", "replaceWith", "Hà Nội", "match", "contains", "caseSensitive", "false");
        assertThat(replace.transform("HN-hn", hn)).isEqualTo("Hà Nội-Hà Nội");
        TransformationContext dotted = ctx("find", "İ", "replaceWith", "I", "match", "contains", "caseSensitive", "false");
        assertThat(replace.transform("xİy", dotted)).isEqualTo("xIy");
    }

    @Test
    void regex_characters_are_literal() {
        assertThat(replace.transform("1.5.0", ctx("find", ".", "replaceWith", ",", "match", "contains"))).isEqualTo("1,5,0");
    }

    @Test
    void replace_configuration() {
        assertThat(replace.validate(ctx())).containsExactly("Parameter 'find' is required.");
        assertThat(replace.validate(ctx("find", "x", "match", "regex")))
                .containsExactly("Parameter 'match' must be 'exact' or 'contains'.");
        assertThat(replace.validate(ctx("find", "x", "caseSensitive", "no")))
                .containsExactly("Parameter 'caseSensitive' must be 'true' or 'false'.");
        assertThat(replace.validate(ctx("find", " "))).isEmpty();
    }

    @Test
    void normalize_the_usual_nulls() {
        for (String empty : List.of("N/A", " - ", "NULL", "  ")) {
            assertThat(normalizeNull.transform(empty, ctx())).as(empty).isNull();
        }
        assertThat(normalizeNull.transform("Hà Nội ", ctx())).isEqualTo("Hà Nội ");
    }

    @Test
    void normalize_own_tokens() {
        assertThat(normalizeNull.transform("Không Có", ctx("tokens", "không có"))).isNull();
        assertThat(normalizeNull.transform("N/A", ctx("tokens", "không có"))).isEqualTo("N/A");
        assertThat(normalizeNull.transform("x", ctx("tokens", "X", "caseSensitive", "true"))).isEqualTo("x");
        String many = ListParams.join(Collections.nCopies(51, "t"));
        assertThat(normalizeNull.validate(ctx("tokens", many))).containsExactly("Parameter 'tokens' must have at most 50 tokens.");
    }

    @Test
    void the_catalog_and_the_importer_subset() {
        assertThat(TransformationCatalog.all()).extracting(Transformation::type).hasSize(8).doesNotHaveDuplicates();
        assertThat(TransformationRegistry.standard().types())
                .containsExactly("dateFormat", "defaultValue", "lowercase", "trim", "uppercase");
        assertThat(TransformationRegistry.of(Set.of("trim", "titleCase")).types()).containsExactly("titleCase", "trim");
        assertThatThrownBy(() -> TransformationRegistry.of(Set.of("trim", "nope"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_items_cannot_hold_line_breaks() {
        assertThat(ListParams.split(ListParams.join(List.of("a", "", "b")))).containsExactly("a", "", "b");
        assertThatThrownBy(() -> ListParams.join(List.of("a\nb"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static TransformationContext ctx(String... keyValues) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put(keyValues[i], keyValues[i + 1]);
        }
        return new TransformationContext("f", FieldType.STRING, params);
    }
}
