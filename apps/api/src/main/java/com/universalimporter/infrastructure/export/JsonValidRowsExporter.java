package com.universalimporter.infrastructure.export;

import com.universalimporter.domain.export.ExportFormat;
import com.universalimporter.domain.export.ValidRowsExporter;
import com.universalimporter.domain.pipeline.RowResult;
import com.universalimporter.domain.schema.TargetField;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * Valid rows as a JSON array (design F10-D3), with the application's mapper, so numbers are plain as in every
 * response. Keys follow the schema, whatever the order of the stored values; JSON is never escaped against formulas.
 */
@Component
public class JsonValidRowsExporter implements ValidRowsExporter {

    private final JsonMapper mapper;

    public JsonValidRowsExporter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExportFormat format() {
        return ExportFormat.JSON;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public String fileSuffix() {
        return "-valid.json";
    }

    /**
     * Closes the generator only on success: closing also ends the open array, which would turn a failed export
     * into a well-formed file with rows missing.
     */
    @Override
    public void write(List<TargetField> fields, Stream<RowResult> rows, OutputStream out) {
        JsonGenerator json = mapper.createGenerator(ExportStreams.keepOpen(out));
        json.writeStartArray();
        rows.filter(ExportStreams::isValid).forEach(row -> {
            json.writeStartObject();
            for (TargetField field : fields) {
                json.writeName(field.name());
                writeValue(json, row.values().get(field.name()));
            }
            json.writeEndObject();
        });
        json.writeEndArray();
        json.close();
    }

    private static void writeValue(JsonGenerator json, Object value) {
        switch (value) {
            case null -> json.writeNull();
            case BigDecimal number -> json.writeNumber(number);
            case Number number -> json.writeNumber(new BigDecimal(number.toString()));
            case Boolean bool -> json.writeBoolean(bool);
            case LocalDate date -> json.writeString(date.toString());
            default -> json.writeString(value.toString());
        }
    }
}
