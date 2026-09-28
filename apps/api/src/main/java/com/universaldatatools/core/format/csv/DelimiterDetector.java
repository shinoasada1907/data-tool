package com.universaldatatools.core.format.csv;

import com.universaldatatools.core.table.Delimiter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Picks the CSV field separator from the start of a file (core-02 IO3): the candidate that splits the most records
 * into the same number of fields, above one. Separators inside quotes do not count. Ties go to the larger field count,
 * then to the order COMMA, SEMICOLON, TAB, PIPE; a file no candidate splits is one column, so COMMA.
 */
public final class DelimiterDetector {

    static final int SAMPLE_RECORDS = 50;

    private DelimiterDetector() {
    }

    public static Delimiter detect(String sample) {
        Delimiter best = Delimiter.COMMA;
        double bestConsistency = 0;
        int bestMode = 1;
        for (Delimiter candidate : Delimiter.values()) {
            Map<Integer, Integer> fieldCounts = fieldCounts(sample, candidate);
            int records = fieldCounts.values().stream().mapToInt(Integer::intValue).sum();
            int mode = 0;
            int modeRecords = 0;
            for (Map.Entry<Integer, Integer> entry : fieldCounts.entrySet()) {
                if (entry.getValue() > modeRecords || entry.getValue() == modeRecords && entry.getKey() > mode) {
                    mode = entry.getKey();
                    modeRecords = entry.getValue();
                }
            }
            if (mode <= 1) {
                continue;
            }
            double consistency = (double) modeRecords / records;
            if (consistency > bestConsistency || consistency == bestConsistency && mode > bestMode) {
                best = candidate;
                bestConsistency = consistency;
                bestMode = mode;
            }
        }
        return best;
    }

    /** Field count → number of records with it, over the first non-blank records; a syntax error ends the sample. */
    private static Map<Integer, Integer> fieldCounts(String sample, Delimiter delimiter) {
        Map<Integer, Integer> counts = new HashMap<>();
        CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(delimiter.symbol()).setIgnoreEmptyLines(true).get();
        try (CSVParser parser = format.parse(new StringReader(sample))) {
            Iterator<CSVRecord> records = parser.iterator();
            int seen = 0;
            while (seen < SAMPLE_RECORDS && records.hasNext()) {
                CSVRecord record = records.next();
                if (record.stream().allMatch(String::isBlank)) {
                    continue;
                }
                counts.merge(record.size(), 1, Integer::sum);
                seen++;
            }
        } catch (IOException | UncheckedIOException e) {
            // A broken quote, or a sample cut inside one: what was read so far is the sample.
        }
        return counts;
    }
}
