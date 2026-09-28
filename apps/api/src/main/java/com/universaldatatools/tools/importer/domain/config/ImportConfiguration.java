package com.universaldatatools.tools.importer.domain.config;

import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;

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
 * @param validations     in schema order, then email before unique (see {@link ValidationConfig#normalized})
 * @param version         persistence version, {@code null} until first stored
 */
public record ImportConfiguration(UUID sessionId, TargetSchema schema, MappingConfig mapping,
                                  TransformationConfig transformations, ValidationConfig validations, Long version) {

    public ImportConfiguration {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(transformations, "transformations");
        Objects.requireNonNull(validations, "validations");
        mapping = mapping.inSchemaOrder(schema);
        transformations = transformations.normalized(schema);
        validations = validations.normalized(schema);
    }

    public static ImportConfiguration empty(UUID sessionId) {
        return new ImportConfiguration(sessionId, TargetSchema.empty(), MappingConfig.empty(),
                TransformationConfig.empty(), ValidationConfig.empty(), null);
    }

    /**
     * Replaces the schema and prunes every section that refers to fields it no longer has (design S3, BE-F05 M3,
     * BE-F06 T6, BE-F07 V6); warnings come section by section: mapping, transformations, validations. A field whose
     * type changes keeps its configuration unless that configuration is no longer valid for the new type.
     */
    public ConfigChange withSchema(TargetSchema schema) {
        List<ProblemItem> warnings = new ArrayList<>();
        MappingConfig prunedMapping = ConfigPruner.prune(mapping, schema.fieldNames(), warnings);
        Pruned<TransformationConfig> prunedTransformations = transformations.prunedFor(schema);
        warnings.addAll(prunedTransformations.warnings());
        Pruned<ValidationConfig> prunedValidations = validations.prunedFor(schema);
        warnings.addAll(prunedValidations.warnings());
        return new ConfigChange(new ImportConfiguration(sessionId, schema, prunedMapping,
                prunedTransformations.section(), prunedValidations.section(), version), warnings);
    }

    /** Replaces the whole mapping; every schema field left unmapped gets a warning, in schema order (BE-F05 M2). */
    public ConfigChange withMapping(MappingConfig mapping) {
        List<ProblemItem> warnings = schema.fields().stream()
                .map(TargetField::name)
                .filter(field -> mapping.forField(field).isEmpty())
                .map(field -> new ProblemItem(field, WarningCode.TARGET_FIELD_UNMAPPED.name(), "Field is not mapped."))
                .toList();
        return new ConfigChange(
                new ImportConfiguration(sessionId, schema, mapping, transformations, validations, version), warnings);
    }

    /** Replaces every transformation step; the caller has checked them against the schema (BE-F06 T4). */
    public ConfigChange withTransformations(TransformationConfig transformations) {
        return new ConfigChange(
                new ImportConfiguration(sessionId, schema, mapping, transformations, validations, version), List.of());
    }

    /**
     * Replaces the user's validation rules; the caller has checked them against the schema and passes on the
     * warnings about rules it dropped (BE-F07 V5).
     */
    public ConfigChange withValidations(ValidationConfig validations, List<ProblemItem> warnings) {
        return new ConfigChange(
                new ImportConfiguration(sessionId, schema, mapping, transformations, validations, version), warnings);
    }
}
