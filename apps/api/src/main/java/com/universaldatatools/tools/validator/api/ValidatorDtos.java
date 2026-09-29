package com.universaldatatools.tools.validator.api;

import com.universaldatatools.core.schema.FieldConstraintsSpec;
import com.universaldatatools.core.schema.SchemaFieldSpec;
import com.universaldatatools.core.schema.SchemaSpec;
import com.universaldatatools.platform.dataset.SourceDto;
import com.universaldatatools.platform.output.OutputDto;
import com.universaldatatools.platform.run.RunSourceDto;
import com.universaldatatools.tools.validator.application.ValidatorRecords;
import com.universaldatatools.tools.validator.application.ValidatorRecords.RowRecord;
import com.universaldatatools.tools.validator.application.ValidatorRows;
import com.universaldatatools.tools.validator.application.ValidatorService.ValidatorRun;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of {@code /api/validator} (tool-05 VD1). */
public final class ValidatorDtos {

    private ValidatorDtos() {
    }

    public record CreateRunRequest(SourceDto source, SchemaDto schema) {
    }

    /** A schema as sent; checked by the service, so every part may be missing or wrong. */
    public record SchemaDto(String name, List<FieldDto> fields) {

        SchemaSpec toSpec() {
            return new SchemaSpec(name, fields == null ? null : fields.stream()
                    .map(field -> field == null ? null : field.toSpec()).toList());
        }
    }

    /** @param type {@code string}, {@code number}, {@code boolean}, {@code date} or {@code email} */
    public record FieldDto(String name, String type, Boolean required, ConstraintsDto constraints) {

        SchemaFieldSpec toSpec() {
            return new SchemaFieldSpec(name, type, required, constraints == null ? null : constraints.toSpec());
        }
    }

    /** @param pattern RE2 syntax, matched against the whole value */
    public record ConstraintsDto(Boolean unique, BigDecimal min, BigDecimal max, Integer minLength, Integer maxLength,
                                 String pattern, String format, String defaultValue) {

        FieldConstraintsSpec toSpec() {
            return new FieldConstraintsSpec(unique, min, max, minLength, maxLength, pattern, format, defaultValue);
        }
    }

    public record ExportRequest(String content, OutputDto output) {
    }

    public record ValidatorRunDto(UUID id, Instant createdAt, Instant expiresAt, List<RunSourceDto> sources,
                                  String schemaName, List<String> fields,
                                  ValidatorRecords.Compatibility compatibility, ValidatorRecords.Summary summary) {

        static ValidatorRunDto of(ValidatorRun run) {
            return new ValidatorRunDto(run.record().id(), run.record().createdAt(), run.record().expiresAt(),
                    run.record().sources().stream().map(RunSourceDto::of).toList(),
                    run.config().schema().name(),
                    run.config().schema().fields().stream().map(field -> field.name()).toList(),
                    run.config().compatibility(), run.summary());
        }
    }

    public record PageDto(int number, int size, long totalElements, long totalPages) {
    }

    /** @param value the source cell the error is about */
    public record ErrorDto(String field, String code, String rule, String message, String value) {
    }

    /** @param values source cells in the order of the run's {@code fields} */
    public record RowDto(long rowNumber, List<String> values, List<ErrorDto> errors) {
    }

    public record ValidatorRowsDto(String view, PageDto page, List<RowDto> rows) {

        static ValidatorRowsDto of(ValidatorRows.Page page) {
            return new ValidatorRowsDto(page.view().name(),
                    new PageDto(page.number(), page.size(), page.totalElements(), page.totalPages()),
                    page.rows().stream().map(row -> row(row, page.fields())).toList());
        }

        private static RowDto row(RowRecord row, List<String> fields) {
            List<ErrorDto> errors = new ArrayList<>(row.errors().size());
            for (ValidatorRecords.ErrorRecord error : row.errors()) {
                int index = fields.indexOf(error.field());
                errors.add(new ErrorDto(error.field(), error.code(), error.rule(), error.message(),
                        index < 0 ? null : row.values().get(index)));
            }
            return new RowDto(row.rowNumber(), row.values(), errors);
        }
    }
}
