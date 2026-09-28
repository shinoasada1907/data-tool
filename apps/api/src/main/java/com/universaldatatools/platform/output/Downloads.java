package com.universaldatatools.platform.output;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sends a file straight into the servlet response, synchronously, for every tool (core-04 PL7; importer design
 * F10-D1). Not {@code StreamingResponseBody}: its async request can time out mid-download and end as a clean,
 * truncated 200. A failure while nothing has reached the client is still a clean {@code 500 EXPORT_FAILED}; once
 * bytes are out the exception goes on to the container, which drops the connection.
 */
public final class Downloads {

    private static final Logger log = LoggerFactory.getLogger(Downloads.class);

    private Downloads() {
    }

    /** Writes a whole file; it never closes {@code out}. */
    @FunctionalInterface
    public interface BodyWriter {
        void writeTo(OutputStream out) throws IOException;
    }

    /** @param what names the download in logs, such as {@code "dataset 1a2b…"}; never data */
    public static void send(HttpServletResponse response, String fileName, String contentType, BodyWriter body,
                            String what) throws IOException {
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        try {
            body.writeTo(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException | RuntimeException e) {
            if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
                log.debug("Client left during the download of {}", what);
                throw e;
            }
            if (!response.isCommitted()) {
                response.reset();
                response.setHeader("X-Content-Type-Options", "nosniff");
                if (e instanceof DomainException domain) {
                    throw domain;
                }
                log.error("Download of {} failed before any byte was sent", what, e);
                throw new DomainException(ErrorCode.EXPORT_FAILED, "Export failed.");
            }
            log.error("Download of {} failed after it started", what, e);
            throw e;
        }
    }
}
