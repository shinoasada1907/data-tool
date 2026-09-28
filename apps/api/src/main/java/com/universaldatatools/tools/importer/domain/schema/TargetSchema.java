package com.universaldatatools.tools.importer.domain.schema;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** The fields the import produces, independent of any business domain (spec: target-schema). */
public record TargetSchema(List<TargetField> fields) {

    public static final int MAX_NAME_LENGTH = 100;

    public TargetSchema {
        fields = List.copyOf(fields);
    }

    public static TargetSchema empty() {
        return new TargetSchema(List.of());
    }

    /**
     * Checks the fields against the naming, type and order rules, reporting every problem in input order.
     * A valid schema has trimmed names and is sorted by order, renumbered from 0.
     *
     * @throws DomainException {@code SCHEMA_INVALID} with one item per problem
     */
    public static TargetSchema define(List<FieldSpec> specs) {
        List<ProblemItem> problems = new ArrayList<>();
        if (specs.isEmpty()) {
            problems.add(problem(null, "Schema must contain at least one field."));
        }
        Set<String> takenNames = new HashSet<>();
        Set<Integer> takenOrders = new HashSet<>();
        List<Checked> checked = new ArrayList<>(specs.size());
        for (FieldSpec spec : specs) {
            String name = spec.name() == null ? "" : spec.name().strip();
            String field = name.isEmpty() ? null : name;
            if (field == null) {
                problems.add(problem(null, "Field name must not be blank."));
            } else if (name.length() > MAX_NAME_LENGTH) {
                problems.add(problem(field, "Field name must be at most " + MAX_NAME_LENGTH + " characters."));
            }
            if (field != null && !takenNames.add(name.toLowerCase(Locale.ROOT))) {
                problems.add(problem(field, "Duplicate field name."));
            }
            Optional<FieldType> type = FieldType.fromCode(spec.type());
            if (type.isEmpty()) {
                problems.add(problem(field, "Unknown field type."));
            }
            if (spec.order() == null) {
                problems.add(problem(field, "Field order is required."));
            } else if (!takenOrders.add(spec.order())) {
                problems.add(problem(field, "Duplicate field order."));
            }
            checked.add(new Checked(name, type.orElse(null), spec.required(), spec.order()));
        }
        if (!problems.isEmpty()) {
            throw new DomainException(ErrorCode.SCHEMA_INVALID, "Target schema is invalid.", problems);
        }
        List<Checked> sorted = checked.stream().sorted(Comparator.comparing(Checked::order)).toList();
        return new TargetSchema(IntStream.range(0, sorted.size())
                .mapToObj(i -> new TargetField(sorted.get(i).name(), sorted.get(i).type(), sorted.get(i).required(), i))
                .toList());
    }

    /** Exact, case-sensitive match: field names are keys in the output JSON. */
    public Optional<TargetField> field(String name) {
        return fields.stream().filter(field -> field.name().equals(name)).findFirst();
    }

    /** Names in schema order. */
    public Set<String> fieldNames() {
        Set<String> names = new LinkedHashSet<>();
        fields.forEach(field -> names.add(field.name()));
        return Collections.unmodifiableSet(names);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    private static ProblemItem problem(String field, String message) {
        return new ProblemItem(field, ErrorCode.SCHEMA_INVALID.name(), message);
    }

    private record Checked(String name, FieldType type, boolean required, Integer order) {
    }
}
