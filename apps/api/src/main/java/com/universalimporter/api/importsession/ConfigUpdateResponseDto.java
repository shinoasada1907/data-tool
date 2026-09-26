package com.universalimporter.api.importsession;

import com.universalimporter.application.configuration.ConfigUpdateResult;
import com.universalimporter.domain.common.ProblemItem;

import java.util.List;

/** Answer of every configuration PUT: the session as stored, and what the change removed (design D3). */
public record ConfigUpdateResponseDto(ImportSessionDto session, List<ProblemItem> warnings) {

    public static ConfigUpdateResponseDto from(ConfigUpdateResult result) {
        return new ConfigUpdateResponseDto(ImportSessionDto.from(result.details()), result.warnings());
    }
}
