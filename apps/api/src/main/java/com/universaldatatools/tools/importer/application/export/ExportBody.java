package com.universaldatatools.tools.importer.application.export;

import java.io.IOException;
import java.io.OutputStream;

/** Writes a prepared download; kept free of web types so the application layer stays framework-neutral (D1). */
@FunctionalInterface
public interface ExportBody {

    void writeTo(OutputStream out) throws IOException;
}
