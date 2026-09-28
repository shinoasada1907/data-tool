package com.universaldatatools.core.format;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Lets a writer "close" its stream without closing the caller's: {@link #close} only flushes. */
public final class KeepOpenOutputStream extends FilterOutputStream {

    public KeepOpenOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        out.write(bytes, offset, length);
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
