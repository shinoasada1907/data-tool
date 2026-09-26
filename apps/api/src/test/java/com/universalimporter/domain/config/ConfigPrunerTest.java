package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigPrunerTest {

    private static final String PRUNED = "Mapping for this field was removed because the field no longer exists.";

    private final List<ProblemItem> warnings = new ArrayList<>();

    @Test
    void fields_missing_from_the_schema_are_dropped_with_one_warning_each_sorted_by_name() {
        FakeSection pruned = ConfigPruner.prune(new FakeSection(Set.of("c", "a", "b")), Set.of("a"), warnings);

        assertThat(pruned.fields()).containsExactly("a");
        assertThat(warnings).containsExactly(
                new ProblemItem("b", "CONFIG_PRUNED", PRUNED),
                new ProblemItem("c", "CONFIG_PRUNED", PRUNED));
    }

    @Test
    void a_section_whose_fields_all_exist_is_kept_without_warnings() {
        FakeSection section = new FakeSection(Set.of("a"));

        FakeSection pruned = ConfigPruner.prune(section, Set.of("a", "b"), warnings);

        assertThat(pruned).isEqualTo(section);
        assertThat(warnings).isEmpty();
    }

    @Test
    void names_are_matched_exactly() {
        FakeSection pruned = ConfigPruner.prune(new FakeSection(Set.of("Email")), Set.of("email"), warnings);

        assertThat(pruned.fields()).isEmpty();
        assertThat(warnings).extracting(ProblemItem::field).containsExactly("Email");
    }

    record FakeSection(Set<String> fields) implements FieldScopedSection<FakeSection> {

        @Override
        public String sectionLabel() {
            return "Mapping";
        }

        @Override
        public Set<String> referencedFields() {
            return fields;
        }

        @Override
        public FakeSection retainFields(Set<String> fieldNames) {
            return new FakeSection(fields.stream().filter(fieldNames::contains).collect(Collectors.toSet()));
        }
    }
}
