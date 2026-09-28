package com.universaldatatools.platform.output;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.Typing;
import com.universaldatatools.core.table.WriteOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * An {@link OutputDto} checked and turned into a format and its {@link WriteOptions} (core-04 PL7). Options of
 * another format are refused rather than ignored, so a client sees its own mistake.
 */
public record OutputSpec(DataFormat format, WriteOptions options) {

    /**
     * @param defaultSheetName the XLSX sheet name when none is asked, such as the source sheet's
     * @throws DomainException {@code CONFIG_INVALID} with one item per problem
     */
    public static OutputSpec from(OutputDto dto, String defaultSheetName) {
        List<ProblemItem> problems = new ArrayList<>();
        if (dto == null) {
            throw invalid(List.of(problem("output is required.")));
        }
        DataFormat format = parse(DataFormat.class, dto.format(), "output.format", problems);
        if (format == null && dto.format() == null) {
            problems.add(problem("output.format is required."));
        }
        if (dto.csv() != null && format != DataFormat.CSV) {
            problems.add(problem("output.csv applies to CSV only."));
        }
        if (dto.json() != null && format != DataFormat.JSON) {
            problems.add(problem("output.json applies to JSON only."));
        }
        if (dto.xlsx() != null && format != DataFormat.XLSX) {
            problems.add(problem("output.xlsx applies to XLSX only."));
        }
        WriteOptions options = format == null ? null : switch (format) {
            case CSV -> csv(dto.csv(), problems);
            case JSON -> json(dto.json(), problems);
            case XLSX -> xlsx(dto.xlsx(), defaultSheetName, problems);
        };
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        return new OutputSpec(format, options);
    }

    private static WriteOptions csv(OutputDto.CsvOptions csv, List<ProblemItem> problems) {
        WriteOptions defaults = WriteOptions.csvDefaults();
        if (csv == null) {
            return defaults;
        }
        Delimiter delimiter = csv.delimiter() == null ? defaults.delimiter()
                : parse(Delimiter.class, csv.delimiter(), "output.csv.delimiter", problems);
        return new WriteOptions(delimiter, orElse(csv.header(), true), orElse(csv.bom(), true),
                orElse(csv.formulaGuard(), true), false, Typing.STRING, null);
    }

    private static WriteOptions json(OutputDto.JsonOptions json, List<ProblemItem> problems) {
        if (json == null) {
            return WriteOptions.jsonDefaults();
        }
        return new WriteOptions(null, true, false, false, orElse(json.pretty(), false),
                typing(json.typing(), "output.json.typing", problems), null);
    }

    private static WriteOptions xlsx(OutputDto.XlsxOptions xlsx, String defaultSheetName, List<ProblemItem> problems) {
        String sheetName = xlsx == null || xlsx.sheetName() == null ? defaultSheetName : xlsx.sheetName();
        Typing typing = xlsx == null ? Typing.PRESERVE : typing(xlsx.typing(), "output.xlsx.typing", problems);
        return new WriteOptions(null, true, false, false, false, typing, sheetName);
    }

    private static Typing typing(String value, String field, List<ProblemItem> problems) {
        return value == null ? Typing.PRESERVE : parse(Typing.class, value, field, problems);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field, List<ProblemItem> problems) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(problem(field + " must be one of " + Arrays.toString(type.getEnumConstants()) + "."));
            return null;
        }
    }

    private static boolean orElse(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static ProblemItem problem(String message) {
        return new ProblemItem(null, ErrorCode.CONFIG_INVALID.name(), message);
    }

    private static DomainException invalid(List<ProblemItem> problems) {
        return new DomainException(ErrorCode.CONFIG_INVALID, "Output options are invalid.", problems);
    }
}
