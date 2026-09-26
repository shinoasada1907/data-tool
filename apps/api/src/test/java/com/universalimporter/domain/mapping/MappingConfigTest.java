package com.universalimporter.domain.mapping;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.source.SourceColumn;
import com.universalimporter.domain.source.SourceSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MappingConfigTest {

    private static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", true, 0),
            new FieldSpec("country", "string", false, 1),
            new FieldSpec("note", "string", false, 2)));
    private static final SourceSchema SOURCE =
            new SourceSchema(List.of(new SourceColumn(0, "Họ tên"), new SourceColumn(1, "email")), 1, null);

    @Test
    void a_valid_mapping_is_sorted_by_schema_order() {
        MappingConfig mapping = define(k("country", "VN"), sc("name", "Họ tên"));

        assertThat(mapping.mappings()).containsExactly(
                new FieldMapping("name", MappingType.SOURCE_COLUMN, "Họ tên", null),
                new FieldMapping("country", MappingType.CONSTANT, null, "VN"));
    }

    @Test
    void mapping_nothing_is_valid() {
        assertThat(define().mappings()).isEmpty();
        assertThat(MappingConfig.empty().mappings()).isEmpty();
    }

    @Test
    void a_missing_source_column_is_source_column_not_found() {
        DomainException ex = failure(sc("name", "Name"));

        assertThat(ex.code()).isEqualTo(ErrorCode.SOURCE_COLUMN_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Source column not found.");
        assertThat(ex.items()).containsExactly(
                new ProblemItem("name", "SOURCE_COLUMN_NOT_FOUND", "Source column does not exist."));
    }

    @Test
    void a_source_column_mapping_needs_a_column() {
        assertThat(invalid(sc("name", null)))
                .containsExactly(item("name", "sourceColumn is required for SOURCE_COLUMN mappings."));
    }

    @Test
    void a_source_column_mapping_has_no_constant() {
        assertThat(invalid(new MappingSpec("name", "SOURCE_COLUMN", "Họ tên", "x")))
                .containsExactly(item("name", "constantValue must be null for SOURCE_COLUMN mappings."));
    }

    @Test
    void a_constant_must_not_be_blank() {
        assertThat(invalid(k("country", "  ")))
                .containsExactly(item("country", "constantValue must not be blank for CONSTANT mappings."));
        assertThat(invalid(k("country", null)))
                .containsExactly(item("country", "constantValue must not be blank for CONSTANT mappings."));
    }

    @Test
    void a_constant_mapping_has_no_source_column() {
        assertThat(invalid(new MappingSpec("country", "CONSTANT", "Họ tên", "VN")))
                .containsExactly(item("country", "sourceColumn must be null for CONSTANT mappings."));
    }

    @Test
    void a_field_is_mapped_at_most_once() {
        assertThat(invalid(sc("name", "Họ tên"), sc("name", "email")))
                .containsExactly(item("name", "Target field is mapped more than once."));
    }

    @Test
    void the_target_field_must_be_in_the_schema() {
        assertThat(invalid(sc("phone", "email")))
                .containsExactly(item("phone", "Target field does not exist in the schema."));
        assertThat(invalid(sc("Name", "email")))
                .containsExactly(item("Name", "Target field does not exist in the schema."));
    }

    @Test
    void the_mapping_type_is_matched_exactly() {
        assertThat(invalid(new MappingSpec("name", "source_column", "Họ tên", null)))
                .containsExactly(item("name", "Unknown mapping type."));
    }

    @Test
    void the_target_field_is_required() {
        assertThat(invalid(new MappingSpec(null, "CONSTANT", null, "x")))
                .containsExactly(item(null, "Target field is required."));
    }

    @Test
    void every_problem_is_reported_and_mapping_invalid_wins_over_a_missing_column() {
        DomainException ex = failure(sc("name", "Name"), sc("phone", "email"));

        assertThat(ex.code()).isEqualTo(ErrorCode.MAPPING_INVALID);
        assertThat(ex.getMessage()).isEqualTo("Mapping is invalid.");
        assertThat(ex.items()).extracting(ProblemItem::code).containsExactly("SOURCE_COLUMN_NOT_FOUND", "MAPPING_INVALID");
    }

    @Test
    void the_mapping_is_a_field_scoped_section() {
        MappingConfig mapping = define(sc("name", "Họ tên"), k("country", "VN"));

        assertThat(mapping.sectionLabel()).isEqualTo("Mapping");
        assertThat(mapping.referencedFields()).containsExactlyInAnyOrder("name", "country");
        assertThat(mapping.retainFields(Set.of("name")).mappings())
                .containsExactly(new FieldMapping("name", MappingType.SOURCE_COLUMN, "Họ tên", null));
        assertThat(mapping.forField("country")).contains(new FieldMapping("country", MappingType.CONSTANT, null, "VN"));
        assertThat(mapping.forField("note")).isEmpty();
    }

    private static MappingSpec sc(String target, String column) {
        return new MappingSpec(target, "SOURCE_COLUMN", column, null);
    }

    private static MappingSpec k(String target, String value) {
        return new MappingSpec(target, "CONSTANT", null, value);
    }

    private static ProblemItem item(String field, String message) {
        return new ProblemItem(field, "MAPPING_INVALID", message);
    }

    private static MappingConfig define(MappingSpec... specs) {
        return MappingConfig.define(List.of(specs), SCHEMA, SOURCE);
    }

    private static DomainException failure(MappingSpec... specs) {
        DomainException ex = catchThrowableOfType(DomainException.class, () -> define(specs));
        assertThat(ex).as("define should fail").isNotNull();
        return ex;
    }

    private static List<ProblemItem> invalid(MappingSpec... specs) {
        DomainException ex = failure(specs);
        assertThat(ex.code()).isEqualTo(ErrorCode.MAPPING_INVALID);
        assertThat(ex.getMessage()).isEqualTo("Mapping is invalid.");
        return ex.items();
    }
}
