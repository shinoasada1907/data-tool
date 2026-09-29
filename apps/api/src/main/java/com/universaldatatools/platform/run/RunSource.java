package com.universaldatatools.platform.run;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.ResolvedReadOptions;

import java.util.UUID;

/**
 * A dataset a run read, as it was then: the run keeps working after the dataset is gone (core-01 TD13).
 *
 * @param role    what the dataset was to the tool: {@code source}, or {@code left}/{@code right} for a diff
 * @param options the read options actually used
 */
public record RunSource(String role, UUID datasetId, String fileName, DataFormat format, ResolvedReadOptions options) {
}
