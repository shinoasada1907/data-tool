package com.universaldatatools.core.table;

/** Type of a source cell when the source has types (JSON tokens, XLSX cell types); CSV has none (core-01 TD5). */
public enum CellKind {
    TEXT, NUMBER, BOOLEAN, DATE
}
