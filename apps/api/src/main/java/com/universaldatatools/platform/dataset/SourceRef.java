package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.ReadOptions;

import java.util.UUID;

/** A dataset as a tool's source: {@code source: {datasetId, options}} of the toolbox contract (core-01). */
public record SourceRef(UUID datasetId, ReadOptions options) {
}
