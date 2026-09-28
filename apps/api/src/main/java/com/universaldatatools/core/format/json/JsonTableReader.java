package com.universaldatatools.core.format.json;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.CellKinds;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.ColumnNames;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.ResolvedReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SheetInfo;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.core.table.TableScan;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JSON that is an array of flat objects (core-02 IO6): element N is row N, columns are the keys in the order first
 * seen, strings stay as they are and numbers keep the exact text of their token. A nested object or array is refused
 * with {@code JSON_NOT_FLAT}; flattening waits for a later stage (Notion 02). Messages never quote the file.
 */
public final class JsonTableReader implements TableReader {

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNumberLength(1_000)
                    .maxStringLength(1_000_000)
                    .maxNestingDepth(16)
                    .build())
            .build();

    @Override
    public DataFormat format() {
        return DataFormat.JSON;
    }

    @Override
    public List<SheetInfo> sheets(InputStream in) {
        return List.of();
    }

    @Override
    public TableInfo inspect(InputStream in, ReadOptions options) {
        Map<String, Integer> keyIndex = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        TableScan scan = new TableScan(List.of(), options.limits());
        try (Elements elements = new Elements(in)) {
            for (Element element = elements.next(); element != null; element = elements.next()) {
                for (String key : element.values().keySet()) {
                    if (!keyIndex.containsKey(key)) {
                        keyIndex.put(key, keys.size());
                        keys.add(key);
                        List<Column> named = ColumnNames.normalize(keys);
                        scan.addColumn(named.getLast());
                    }
                }
                scan.accept(element.toRow(keyIndex, keys.size()));
            }
            if (elements.count() == 0) {
                throw new DomainException(ErrorCode.FILE_EMPTY, "JSON array is empty.");
            }
        }
        ResolvedReadOptions resolved = new ResolvedReadOptions(null, null, null, true);
        return new TableInfo(DataFormat.JSON, resolved, Set.of(), null, List.of(), ColumnNames.normalize(keys), keys,
                scan.profiles(), scan.rowCount(), 0);
    }

    @Override
    public Stream<Row> read(InputStream in, TableInfo info) {
        Map<String, Integer> keyIndex = new HashMap<>();
        for (int i = 0; i < info.sourceKeys().size(); i++) {
            keyIndex.put(info.sourceKeys().get(i), i);
        }
        int width = info.columns().size();
        Elements elements = new Elements(in);
        Iterator<Row> rows = new Iterator<>() {
            private Row next;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    Element element = elements.next();
                    next = element == null ? null : element.toRow(keyIndex, width);
                }
                return next != null;
            }

            @Override
            public Row next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Row row = next;
                next = null;
                return row;
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED), false)
                .onClose(elements::close);
    }

    /** One object of the array: its values and their kinds by key, in file order. */
    private record Element(long number, Map<String, String> values, Map<String, CellKind> kinds) {

        Row toRow(Map<String, Integer> keyIndex, int width) {
            String[] cells = new String[width];
            CellKind[] cellKinds = new CellKind[width];
            values.forEach((key, value) -> {
                int index = keyIndex.get(key);
                cells[index] = value;
                cellKinds[index] = kinds.get(key);
            });
            return new Row(number, Arrays.asList(cells), CellKinds.of(Arrays.asList(cellKinds)));
        }
    }

    /** Walks the top-level array element by element. */
    private static final class Elements implements AutoCloseable {

        private final JsonParser parser;
        private long count;
        private boolean done;

        Elements(InputStream in) {
            try {
                this.parser = FACTORY.createParser(ObjectReadContext.empty(), in);
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new DomainException(ErrorCode.FILE_PARSE_ERROR, "JSON must be an array of objects.");
                }
            } catch (JacksonException e) {
                throw new DomainException(ErrorCode.FILE_PARSE_ERROR, "JSON must be an array of objects.");
            }
        }

        long count() {
            return count;
        }

        /** The next element, or {@code null} after the last one. */
        Element next() {
            if (done) {
                return null;
            }
            try {
                JsonToken token = parser.nextToken();
                if (token == JsonToken.END_ARRAY) {
                    done = true;
                    if (parser.nextToken() != null) {
                        throw invalid();
                    }
                    return null;
                }
                count++;
                if (token != JsonToken.START_OBJECT) {
                    throw new DomainException(ErrorCode.FILE_PARSE_ERROR,
                            "Element " + count + " of the array is not an object.");
                }
                return object();
            } catch (JacksonException e) {
                throw invalid();
            }
        }

        private Element object() {
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, CellKind> kinds = new HashMap<>();
            Set<String> seen = new HashSet<>();
            for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
                String key = parser.currentName();
                if (!seen.add(key)) {
                    throw new DomainException(ErrorCode.FILE_PARSE_ERROR,
                            "Duplicate key \"" + key + "\" at row " + count + ".");
                }
                JsonToken value = parser.nextToken();
                switch (value) {
                    case VALUE_STRING -> put(values, kinds, key, parser.getString(), CellKind.TEXT);
                    case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> put(values, kinds, key, parser.getString(),
                            CellKind.NUMBER);
                    case VALUE_TRUE -> put(values, kinds, key, "true", CellKind.BOOLEAN);
                    case VALUE_FALSE -> put(values, kinds, key, "false", CellKind.BOOLEAN);
                    case VALUE_NULL -> values.put(key, null);
                    case START_OBJECT, START_ARRAY -> throw new DomainException(ErrorCode.JSON_NOT_FLAT,
                            "Nested value at row " + count + ", key \"" + key + "\" is not supported yet.");
                    default -> throw invalid();
                }
            }
            return new Element(count, values, kinds);
        }

        private static void put(Map<String, String> values, Map<String, CellKind> kinds, String key, String value,
                                CellKind kind) {
            values.put(key, value);
            kinds.put(key, kind);
        }

        /** Never Jackson's message: it may quote the file. */
        private DomainException invalid() {
            return new DomainException(ErrorCode.FILE_PARSE_ERROR,
                    "Invalid JSON near row " + Math.max(count, 1) + ".");
        }

        @Override
        public void close() {
            parser.close();
        }
    }
}
