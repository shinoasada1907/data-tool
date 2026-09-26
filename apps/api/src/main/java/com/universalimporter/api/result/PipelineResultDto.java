package com.universalimporter.api.result;

import com.universalimporter.api.process.PipelineSummaryDto;
import com.universalimporter.application.pipeline.PipelineSummaryView;
import com.universalimporter.application.result.ResultPage;
import com.universalimporter.domain.pipeline.ImportError;
import com.universalimporter.domain.pipeline.RowResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** One page of a result (spec: import-result); {@code view} is lower case, as the client sends it. */
public record PipelineResultDto(PipelineSummaryDto summary, String view, PageDto page, List<ResultRowDto> rows) {

    public record PageDto(int number, int size, long totalElements, int totalPages) {
    }

    /** {@code values} in schema order; a valid row's are typed, an invalid row's are text or null (D10). */
    public record ResultRowDto(int rowNumber, boolean valid, Map<String, Object> values, List<ImportErrorDto> errors) {
    }

    public record ImportErrorDto(int rowNumber, String fieldName, String stage, String rule, Integer step, String code,
                                 String message, String sourceValue) {
    }

    static PipelineResultDto from(UUID sessionId, ResultPage page) {
        return new PipelineResultDto(
                PipelineSummaryDto.from(new PipelineSummaryView(sessionId, page.status(), page.summary())),
                page.view().name().toLowerCase(Locale.ROOT),
                new PageDto(page.page(), page.size(), page.totalElements(), page.totalPages()),
                page.rows().stream().map(PipelineResultDto::row).toList());
    }

    private static ResultRowDto row(RowResult row) {
        return new ResultRowDto(row.rowNumber(), row.valid(), row.values(),
                row.errors().stream().map(PipelineResultDto::error).toList());
    }

    private static ImportErrorDto error(ImportError error) {
        return new ImportErrorDto(error.rowNumber(), error.fieldName(), error.stage().name(), error.rule(), error.step(),
                error.code().name(), error.message(), error.sourceValue());
    }
}
