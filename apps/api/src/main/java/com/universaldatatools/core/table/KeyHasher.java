package com.universaldatatools.core.table;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Turns a key of one or more values into 128 bits (core-02 IO11), so dataset-wide indexes hold a fixed size per key
 * whatever the length of the values. Each part is length-prefixed, so {@code ["ab","c"]} and {@code ["a","bc"]}
 * differ, and {@code null} differs from the empty string. Normalizing the values is the caller's job. Not
 * thread-safe: one hasher per run.
 */
public final class KeyHasher {

    private static final int NULL_LENGTH = -1;

    private final MessageDigest digest;

    public KeyHasher() {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JDK has SHA-256", e);
        }
    }

    public Hash128 hash(List<String> parts) {
        digest.reset();
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        for (String part : parts) {
            byte[] bytes = part == null ? null : part.getBytes(StandardCharsets.UTF_8);
            length.clear();
            length.putInt(bytes == null ? NULL_LENGTH : bytes.length);
            digest.update(length.array());
            if (bytes != null) {
                digest.update(bytes);
            }
        }
        ByteBuffer hash = ByteBuffer.wrap(digest.digest());
        return new Hash128(hash.getLong(), hash.getLong());
    }
}
