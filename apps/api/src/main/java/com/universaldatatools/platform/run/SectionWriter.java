package com.universaldatatools.platform.run;

import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** One NDJSON section of a run being written: a JSON document per line, UTF-8. */
public final class SectionWriter {

    private static final int BUFFER = 64 * 1024;

    private final JsonMapper json;
    private final FileChannel channel;
    private final OutputStream out;
    private boolean closed;

    SectionWriter(Path file, JsonMapper json) {
        this.json = json;
        try {
            this.channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create run section " + file.getFileName(), e);
        }
        this.out = new BufferedOutputStream(Channels.newOutputStream(channel), BUFFER);
    }

    public void write(Object record) {
        try {
            out.write(json.writeValueAsBytes(record));
            out.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write a run section", e);
        }
    }

    /** Flushes and syncs to disk, so a committed run survives a crash right after the rename. */
    void finish() {
        try (OutputStream closing = out) {
            closing.flush();
            channel.force(true);
            closed = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot finish a run section", e);
        }
    }

    void closeQuietly() {
        if (!closed) {
            try {
                out.close();
            } catch (IOException e) {
                // Being discarded anyway.
            }
            closed = true;
        }
    }
}
