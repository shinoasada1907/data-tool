package com.universaldatatools.core.table;

/** A sheet of a workbook; {@code visible} is false for hidden and very hidden sheets. */
public record SheetInfo(String name, boolean visible) {
}
