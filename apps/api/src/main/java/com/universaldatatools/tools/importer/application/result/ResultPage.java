package com.universaldatatools.tools.importer.application.result;

import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;
import com.universaldatatools.tools.importer.domain.pipeline.ResultView;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;

import java.util.List;

/** A page of rows with the whole result's summary, which no filter or page changes. */
public record ResultPage(ResultSummary summary, SessionStatus status, ResultView view, int page, int size,
                         long totalElements, int totalPages, List<RowResult> rows) {

    public ResultPage {
        rows = List.copyOf(rows);
    }
}
