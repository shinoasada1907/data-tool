package com.universaldatatools.core.format.xlsx;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Scans an uploaded workbook once before it is parsed, and rejects it as soon as it would inflate beyond the
 * configured limits (design X4). Stops reading at the first violation instead of inflating the rest.
 */
@Component
public class XlsxZipGuard {

    /** Entries smaller than this are never rejected for their ratio: tiny XML parts compress extremely well. */
    private static final long RATIO_CHECK_THRESHOLD = 1024 * 1024;

    private final XlsxLimits limits;

    public XlsxZipGuard(XlsxLimits limits) {
        this.limits = limits;
    }

    public void check(InputStream input) {
        CountingInputStream compressed = new CountingInputStream(input);
        long maxTotal = limits.maxUncompressedSize().toBytes();
        byte[] buffer = new byte[64 * 1024];
        // commons-compress, like the XLSX reader itself: java.util.zip.ZipInputStream trusts the sizes declared
        // in local headers and rejects valid workbooks whose local headers declare 0 (sizes in the central
        // directory only). Sizes are never trusted here anyway: inflated bytes are counted as they come.
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(compressed, "UTF-8", true, true)) {
            int entries = 0;
            long total = 0;
            for (ZipArchiveEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > limits.maxEntries()) {
                    throw tooLarge();
                }
                long compressedAtStart = compressed.count();
                long entrySize = 0;
                for (int read = zip.read(buffer); read > 0; read = zip.read(buffer)) {
                    entrySize += read;
                    total += read;
                    long entryCompressed = Math.max(1, compressed.count() - compressedAtStart);
                    if (total > maxTotal || entrySize > RATIO_CHECK_THRESHOLD
                            && entrySize > (long) limits.maxInflateRatio() * entryCompressed) {
                        throw tooLarge();
                    }
                }
            }
            if (entries == 0) {
                throw notAWorkbook();
            }
        } catch (IOException e) {
            throw notAWorkbook();
        }
    }

    private static DomainException tooLarge() {
        return new DomainException(ErrorCode.FILE_PARSE_ERROR, "XLSX file expands beyond the allowed limits.");
    }

    static DomainException notAWorkbook() {
        return new DomainException(ErrorCode.FILE_PARSE_ERROR, "File is not a valid XLSX workbook.");
    }

    /** Counts the compressed bytes the zip reader has pulled from the upload. */
    private static final class CountingInputStream extends FilterInputStream {

        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        long count() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            count += skipped;
            return skipped;
        }
    }
}
