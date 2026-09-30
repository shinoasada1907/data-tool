package com.universaldatatools.core.table;

/** Field separators a CSV dataset may use; the API names them rather than sending the characters (core-01 TD6). */
public enum Delimiter {
    COMMA(','), SEMICOLON(';'), TAB('\t'), PIPE('|');

    private final char symbol;

    Delimiter(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }
}
