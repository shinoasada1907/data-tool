package com.universalimporter.domain.transformation;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.Pruned;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransformationConfigPruneTest {

    private static final TargetSchema NAME_ONLY = TargetSchema.define(List.of(new FieldSpec("name", "string", false, 0)));
    private static final TargetSchema DOB_DATE = TargetSchema.define(List.of(new FieldSpec("dob", "date", false, 0)));

    @Test
    void steps_of_removed_fields_are_dropped_with_one_warning_per_field() {
        Pruned<TransformationConfig> pruned = config(trim("phone"), trim("name")).prunedFor(NAME_ONLY);

        assertThat(pruned.section().transformations()).containsExactly(trim("name"));
        assertThat(pruned.warnings()).containsExactly(new ProblemItem("phone", "CONFIG_PRUNED",
                "Transformations removed because field 'phone' no longer exists."));
    }

    @Test
    void a_date_format_step_with_non_iso_output_is_dropped_when_the_field_becomes_a_date() {
        Pruned<TransformationConfig> pruned = config(date(Map.of("inputFormat", "dd/MM/yyyy", "outputFormat", "dd/MM/yyyy")))
                .prunedFor(DOB_DATE);

        assertThat(pruned.section().transformations()).isEmpty();
        assertThat(pruned.warnings()).containsExactly(new ProblemItem("dob", "CONFIG_PRUNED",
                "dateFormat step 0 removed because field 'dob' is now of type date and must output yyyy-MM-dd."));
    }

    @Test
    void a_date_format_step_with_iso_output_is_kept() {
        TransformationConfig defaultOutput = config(date(Map.of("inputFormat", "dd/MM/yyyy")));
        TransformationConfig explicitIso = config(date(Map.of("inputFormat", "dd/MM/yyyy", "outputFormat", "yyyy-MM-dd")));

        assertThat(defaultOutput.prunedFor(DOB_DATE)).isEqualTo(new Pruned<>(defaultOutput, List.of()));
        assertThat(explicitIso.prunedFor(DOB_DATE)).isEqualTo(new Pruned<>(explicitIso, List.of()));
    }

    @Test
    void steps_that_are_still_valid_are_kept() {
        TransformationConfig config = config(trim("name"));

        assertThat(config.prunedFor(NAME_ONLY)).isEqualTo(new Pruned<>(config, List.of()));
    }

    private static TransformationConfig config(TransformationStep... steps) {
        return new TransformationConfig(List.of(steps));
    }

    private static TransformationStep trim(String field) {
        return new TransformationStep(field, 0, "trim", null);
    }

    private static TransformationStep date(Map<String, String> params) {
        return new TransformationStep("dob", 0, "dateFormat", params);
    }
}
