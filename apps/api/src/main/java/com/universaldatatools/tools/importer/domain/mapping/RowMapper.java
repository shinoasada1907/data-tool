package com.universaldatatools.tools.importer.domain.mapping;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.tools.importer.domain.importsession.SourceSchema;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a source row into the raw value of every target field, in schema order (spec: field-mapping). These are
 * the values before transformation, which is also the {@code sourceValue} of a row error (design D10).
 */
public final class RowMapper {

    /** One entry per schema field, in schema order; the strategy is {@code null} for an unmapped field. */
    private record Slot(String targetField, ResolvedMapping mapping, MappingStrategy strategy) {
    }

    private final List<Slot> slots;

    private RowMapper(List<Slot> slots) {
        this.slots = List.copyOf(slots);
    }

    /**
     * Resolves column names to indexes once, so mapping a row never searches by name.
     *
     * @throws IllegalStateException when a mapping names a column the source does not have; {@link
     *                               MappingConfig#define} rejects that, so it is a programming error here
     */
    public static RowMapper of(TargetSchema schema, MappingConfig mapping, SourceSchema source,
                               MappingStrategies strategies) {
        Map<String, Integer> indexes = source.columns().stream()
                .collect(Collectors.toMap(Column::name, Column::index));
        List<Slot> slots = new ArrayList<>(schema.fields().size());
        for (TargetField field : schema.fields()) {
            slots.add(mapping.forField(field.name())
                    .map(fieldMapping -> resolve(fieldMapping, indexes))
                    .map(resolved -> new Slot(field.name(), resolved, strategies.strategyFor(resolved.type())))
                    .orElseGet(() -> new Slot(field.name(), null, null)));
        }
        return new RowMapper(slots);
    }

    /** Raw values keyed by target field, in schema order; an unmapped field is {@code null}. */
    public LinkedHashMap<String, String> map(Row row) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            values.put(slots.get(i).targetField(), mapField(row, i));
        }
        return values;
    }

    /**
     * The raw value of the schema's {@code fieldIndex}-th field, so a caller can handle one field's failure on its
     * own. A strategy with a bug throws here.
     */
    public String mapField(Row row, int fieldIndex) {
        Slot slot = slots.get(fieldIndex);
        return slot.strategy() == null ? null : slot.strategy().map(row, slot.mapping());
    }

    private static ResolvedMapping resolve(FieldMapping mapping, Map<String, Integer> indexes) {
        if (mapping.type() == MappingType.CONSTANT) {
            return new ResolvedMapping(mapping.targetField(), mapping.type(), -1, mapping.constantValue());
        }
        Integer index = indexes.get(mapping.sourceColumn());
        if (index == null) {
            throw new IllegalStateException("Mapped source column is missing from the source schema");
        }
        return new ResolvedMapping(mapping.targetField(), mapping.type(), index, null);
    }
}
