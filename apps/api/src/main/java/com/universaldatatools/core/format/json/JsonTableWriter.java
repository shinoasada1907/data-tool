package com.universaldatatools.core.format.json;

import com.universaldatatools.core.format.KeepOpenOutputStream;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.CellTyping;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TableWriter;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.core.table.WriteOptions;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An array of objects keyed by column name, UTF-8, no BOM (core-02 IO9). A number is written with the exact text it
 * came with, so {@code 12.50} stays {@code 12.50}; text that is not a JSON number stays a string.
 */
public final class JsonTableWriter implements TableWriter {

    private static final Pattern JSON_NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public DataFormat format() {
        return DataFormat.JSON;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public String extension() {
        return "json";
    }

    @Override
    public RowSink open(OutputStream out, List<OutputColumn> columns, WriteOptions options) {
        KeepOpenOutputStream target = new KeepOpenOutputStream(out);
        JsonGenerator json = options.pretty()
                ? MAPPER.writerWithDefaultPrettyPrinter().createGenerator(target)
                : MAPPER.createGenerator(target);
        json.writeStartArray();
        return new RowSink() {
            @Override
            public void write(List<TypedCell> cells) {
                json.writeStartObject();
                for (int i = 0; i < cells.size(); i++) {
                    OutputColumn column = columns.get(i);
                    json.writeName(column.name());
                    TypedCell cell = cells.get(i);
                    writeValue(json, cell.text(), CellTyping.resolve(cell, options.typing(), column.profile()));
                }
                json.writeEndObject();
            }

            /** Ends the array only here: a failed export must not become a well-formed file with rows missing. */
            @Override
            public void close() {
                json.writeEndArray();
                json.close();
            }

            @Override
            public void abort() {
                json.flush();
            }
        };
    }

    private static void writeValue(JsonGenerator json, String text, CellKind kind) {
        if (kind == null) {
            json.writeNull();
            return;
        }
        switch (kind) {
            case NUMBER -> {
                if (JSON_NUMBER.matcher(text).matches()) {
                    json.writeNumber(text);
                } else {
                    json.writeString(text);
                }
            }
            case BOOLEAN -> {
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.equals("true") || lower.equals("1")) {
                    json.writeBoolean(true);
                } else if (lower.equals("false") || lower.equals("0")) {
                    json.writeBoolean(false);
                } else {
                    json.writeString(text);
                }
            }
            case TEXT, DATE -> json.writeString(text);
        }
    }
}
