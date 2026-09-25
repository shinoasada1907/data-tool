package com.universalimporter.domain.importsession;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;

import java.util.Locale;

/**
 * Decides whether an upload is a CSV or an XLSX from its extension and first bytes (design D5).
 * The client's MIME type is deliberately ignored: browsers disagree about it.
 */
public final class FileTypeDetector {

    /** How many leading bytes callers should pass as {@code head}. */
    public static final int HEAD_SIZE = 8192;

    /** ZIP local file header; every .xlsx is a ZIP archive. */
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};

    private FileTypeDetector() {
    }

    public static SourceFileType detect(String sanitizedName, byte[] head) {
        SourceFileType type = byExtension(sanitizedName);
        if (head.length == 0) {
            throw new DomainException(ErrorCode.FILE_EMPTY, "File is empty.");
        }
        switch (type) {
            case XLSX -> {
                if (!startsWith(head, ZIP_SIGNATURE)) {
                    throw unsupported("File content does not match the .xlsx format.");
                }
            }
            case CSV -> {
                if (containsNulByte(head)) {
                    throw unsupported("File content is not a text CSV.");
                }
            }
        }
        return type;
    }

    private static SourceFileType byExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return SourceFileType.CSV;
        }
        if (lower.endsWith(".xlsx")) {
            return SourceFileType.XLSX;
        }
        throw unsupported("Only .csv and .xlsx files are supported.");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNulByte(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static DomainException unsupported(String message) {
        return new DomainException(ErrorCode.FILE_UNSUPPORTED, message);
    }
}
