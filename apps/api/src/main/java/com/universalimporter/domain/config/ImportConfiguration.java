package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.transformation.TransformationConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything configured for one session, kept apart from the session itself (design S2). A session without a
 * stored configuration has the {@link #empty} one.
 * <p>
 * Every section is kept in a canonical order derived from the schema, whichever way the configuration was built
 * (sent, pruned or loaded), so equal content always compares and hashes equal; a schema reorder reorders them.
 *
 * @param mapping         in schema order (see {@link MappingConfig#inSchemaOrder})
 * @param transformations in schema order, then step order (see {@link TransformationConfig#normalized})
 * @param version         persistence version, {@code null} until first stored
 */
public record ImportConfiguration(UUID sessionId, TargetSchema schema, MappingConfig mapping,
                                  TransformationConfig transformations, Long version) {

    public ImportConfiguration {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(transformations, "transformations");
        mapping = mapping.inSchemaOrder(schema);
        transformations = transformations.normalized(schema);
    }

    public static ImportConfiguration empty(UUID sessionId) {
        return new ImportConfiguration(sessionId, TargetSchema.empty(), MappingConfig.empty(),
                TransformationConfig.empty(), null);
    }

    /**
     * Replaces the schema and prunes every section that refers to fields it no longer has (design S3, BE-F05 M3,
     * BE-F06 T6). A field whose type changes keeps its configuration unless that configuration is no longer valid
     * for the new type.
     */
    public ConfigChange withSchema(TargetSchema schema) {
        List<ProblemItem> warnings = new ArrayList<>();
        MappingConfig prunedMapping = ConfigPruner.prune(mapping, schema.fieldNames(), warnings);
        Pruned<TransformationConfig> prunedTransformations = transformations.prunedFor(schema);
        warnings.addAll(prunedTransformations.warnings());
        return new ConfigChange(new ImportConfiguration(sessionId, schema, prunedMapping,
                prunedTransformations.section(), version), warnings);
    }

    /** Replaces the whole mapping; every schema field left unmapped gets a warning, in schema order (BE-F05 M2). */
    public ConfigChange withMapping(MappingConfig mapping) {
        List<ProblemItem> warnings = schema.fields().stream()
                .map(TargetField::name)
                .filter(field -> mapping.forField(field).isEmpty())
                .map(field -> new ProblemItem(field, WarningCode.TARGET_FIELD_UNMAPPED.name(), "Field is not mapped."))
                .toList();
        return new ConfigChange(new ImportConfiguration(sessionId, schema, mapping, transformations, version), warnings);
    }

    /** Replaces every transformation step; the caller has checked them against the schema (BE-F06 T4). */
    public ConfigChange withTransformations(TransformationConfig transformations) {
        return new ConfigChange(new ImportConfiguration(sessionId, schema, mapping, transformations, version),
                List.of());
    }
}
