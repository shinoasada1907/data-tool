package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.core.common.ProblemItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A readiness issue or configuration warning as the importer's API has shown it since V0.1: no {@code pointer},
 * so the documented {@code ProblemItem} and the JSON stay as they were.
 */
@Schema(name = "ProblemItem")
public record IssueDto(String field, String code, String message) {

    static List<IssueDto> of(List<ProblemItem> items) {
        return items.stream().map(item -> new IssueDto(item.field(), item.code(), item.message())).toList();
    }
}
