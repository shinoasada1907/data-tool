package com.universaldatatools.platform.run;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.platform.dataset.DatasetDtos;

import java.util.UUID;

/** An item of {@code sources} in every run DTO (API contract Toolbox v1, "Run chung"). */
public record RunSourceDto(String role, UUID datasetId, String fileName, DataFormat format,
                           DatasetDtos.OptionsDto options) {

    public static RunSourceDto of(RunSource source) {
        return new RunSourceDto(source.role(), source.datasetId(), source.fileName(), source.format(),
                DatasetDtos.OptionsDto.of(source.options()));
    }
}
