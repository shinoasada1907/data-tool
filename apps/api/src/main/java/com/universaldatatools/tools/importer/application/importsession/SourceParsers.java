package com.universaldatatools.tools.importer.application.importsession;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.SourceParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** The parsers available to this application, looked up by file type. */
@Component
public class SourceParsers {

    private final List<SourceParser> parsers;

    public SourceParsers(List<SourceParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public Optional<SourceParser> find(DataFormat type) {
        return parsers.stream().filter(parser -> parser.supports(type)).findFirst();
    }
}
