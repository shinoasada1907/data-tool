package com.universalimporter.domain.source;

/**
 * A column of the source file.
 *
 * @param index position in the file, from 0
 * @param name  unique, non-blank name (see {@link ColumnNames#normalize})
 */
public record SourceColumn(int index, String name) {
}
