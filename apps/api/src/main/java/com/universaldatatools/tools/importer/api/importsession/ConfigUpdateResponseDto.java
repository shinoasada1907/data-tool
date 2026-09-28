package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.tools.importer.application.configuration.ConfigUpdateResult;
import com.universaldatatools.core.common.ProblemItem;

import java.util.List;

/** Answer of every configuration PUT: the session as stored, and what the change removed (design D3). */
public record ConfigUpdateResponseDto(ImportSessionDto session, List<ProblemItem> warnings) {

    public static ConfigUpdateResponseDto from(ConfigUpdateResult result) {
        return new ConfigUpdateResponseDto(ImportSessionDto.from(result.details()), result.warnings());
    }
}
