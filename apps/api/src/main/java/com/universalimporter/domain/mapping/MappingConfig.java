package com.universalimporter.domain.mapping;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.FieldScopedSection;
import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.source.SourceColumn;
import com.universalimporter.domain.source.SourceSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Which target fields are mapped, and from where (spec: field-mapping). Unmapped fields are simply absent. */
public record MappingConfig(List<FieldMapping> mappings) implements FieldScopedSection<MappingConfig> {

    public MappingConfig {
        mappings = List.copyOf(mappings);
    }

    public static MappingConfig empty() {
        return new MappingConfig(List.of());
    }

    /**
     * Checks the mappings against the schema and the source columns, reporting every problem in input order.
     * A valid mapping is sorted by schema order, so the same mapping sent in another order hashes the same.
     * Messages never contain a source column name: it comes from the file (design D13).
     *
     * @throws DomainException {@code MAPPING_INVALID} when any item is, otherwise {@code SOURCE_COLUMN_NOT_FOUND}
     */
    public static MappingConfig define(List<MappingSpec> specs, TargetSchema schema, SourceSchema source) {
        Set<String> columns = source.columns().stream().map(SourceColumn::name).collect(Collectors.toSet());
        List<ProblemItem> problems = new ArrayList<>();
        Set<String> mapped = new HashSet<>();
        List<FieldMapping> valid = new ArrayList<>(specs.size());
        for (MappingSpec spec : specs) {
            String target = spec.targetField();
            String field = target == null || target.isBlank() ? null : target;
            if (field == null) {
                problems.add(invalid(null, "Target field is required."));
            } else if (schema.field(field).isEmpty()) {
                problems.add(invalid(field, "Target field does not exist in the schema."));
            } else if (!mapped.add(field)) {
                problems.add(invalid(field, "Target field is mapped more than once."));
            }
            Optional<MappingType> type = MappingType.fromName(spec.mappingType());
            if (type.isEmpty()) {
                problems.add(invalid(field, "Unknown mapping type."));
            } else if (type.get() == MappingType.SOURCE_COLUMN) {
                checkSourceColumn(spec, field, columns, problems);
            } else {
                checkConstant(spec, field, problems);
            }
            type.ifPresent(t -> valid.add(new FieldMapping(field, t, spec.sourceColumn(), spec.constantValue())));
        }
        if (!problems.isEmpty()) {
            throw failure(problems);
        }
        return new MappingConfig(valid.stream()
                .sorted(Comparator.comparingInt(mapping -> schema.field(mapping.targetField())
                        .map(TargetField::order).orElseThrow()))
                .toList());
    }

    public Optional<FieldMapping> forField(String targetField) {
        return mappings.stream().filter(mapping -> mapping.targetField().equals(targetField)).findFirst();
    }

    @Override
    public String sectionLabel() {
        return "Mapping";
    }

    @Override
    public Set<String> referencedFields() {
        Set<String> fields = new LinkedHashSet<>();
        mappings.forEach(mapping -> fields.add(mapping.targetField()));
        return Collections.unmodifiableSet(fields);
    }

    @Override
    public MappingConfig retainFields(Set<String> fieldNames) {
        return new MappingConfig(mappings.stream().filter(mapping -> fieldNames.contains(mapping.targetField())).toList());
    }

    private static void checkSourceColumn(MappingSpec spec, String field, Set<String> columns,
                                          List<ProblemItem> problems) {
        String column = spec.sourceColumn();
        if (column == null || column.isBlank()) {
            problems.add(invalid(field, "sourceColumn is required for SOURCE_COLUMN mappings."));
        }
        if (spec.constantValue() != null) {
            problems.add(invalid(field, "constantValue must be null for SOURCE_COLUMN mappings."));
        }
        if (column != null && !column.isBlank() && !columns.contains(column)) {
            problems.add(new ProblemItem(field, ErrorCode.SOURCE_COLUMN_NOT_FOUND.name(), "Source column does not exist."));
        }
    }

    private static void checkConstant(MappingSpec spec, String field, List<ProblemItem> problems) {
        if (spec.constantValue() == null || spec.constantValue().isBlank()) {
            problems.add(invalid(field, "constantValue must not be blank for CONSTANT mappings."));
        }
        if (spec.sourceColumn() != null) {
            problems.add(invalid(field, "sourceColumn must be null for CONSTANT mappings."));
        }
    }

    private static DomainException failure(List<ProblemItem> problems) {
        boolean anyInvalid = problems.stream().anyMatch(p -> p.code().equals(ErrorCode.MAPPING_INVALID.name()));
        return anyInvalid
                ? new DomainException(ErrorCode.MAPPING_INVALID, "Mapping is invalid.", problems)
                : new DomainException(ErrorCode.SOURCE_COLUMN_NOT_FOUND, "Source column not found.", problems);
    }

    private static ProblemItem invalid(String field, String message) {
        return new ProblemItem(field, ErrorCode.MAPPING_INVALID.name(), message);
    }
}
