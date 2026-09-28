package com.universaldatatools.core.table;

/** The first 128 bits of a SHA-256, as a map key (see {@link KeyHasher}). */
public record Hash128(long high, long low) {
}
