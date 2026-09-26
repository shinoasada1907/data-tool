package com.universalimporter.domain.transformation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformationRegistryTest {

    private final TransformationRegistry registry = TransformationRegistry.standard();

    @Test
    void finds_a_transformation_by_its_exact_type() {
        assertThat(registry.find("trim")).get().isInstanceOf(TrimTransformation.class);
        assertThat(registry.find("TRIM")).isEmpty();
        assertThat(registry.find("replace")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void lists_the_types_alphabetically() {
        assertThat(registry.types()).containsExactly("dateFormat", "defaultValue", "lowercase", "trim", "uppercase");
    }

    @Test
    void two_transformations_of_one_type_are_a_programming_error() {
        assertThatThrownBy(() -> new TransformationRegistry(List.of(new TrimTransformation(), new TrimTransformation())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
