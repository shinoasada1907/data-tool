package com.universaldatatools.core.table;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;

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

    /** The Importer's upload (V0.1): {@code .csv} or {@code .xlsx} only. */
    public static DataFormat detect(String sanitizedName, byte[] head) {
        return detect(sanitizedName, head, false);
    }

    /**
     * A toolbox dataset (core-01 TD6): also {@code .json}, whose first character other than whitespace must be
     * {@code [}, and CSV in UTF-16, recognised by its byte order mark.
     */
    public static DataFormat detectDataset(String sanitizedName, byte[] head) {
        return detect(sanitizedName, head, true);
    }

    private static DataFormat detect(String sanitizedName, byte[] head, boolean dataset) {
        DataFormat type = byExtension(sanitizedName, dataset);
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
                if (containsNulByte(head) && !(dataset && startsWithUtf16Bom(head))) {
                    throw unsupported("File content is not a text CSV.");
                }
            }
            case JSON -> {
                if (firstNonBlank(head) != '[') {
                    throw new DomainException(ErrorCode.FILE_PARSE_ERROR, "JSON must be an array of objects.");
                }
            }
        }
        return type;
    }

    private static DataFormat byExtension(String name, boolean dataset) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return DataFormat.CSV;
        }
        if (lower.endsWith(".xlsx")) {
            return DataFormat.XLSX;
        }
        if (dataset && lower.endsWith(".json")) {
            return DataFormat.JSON;
        }
        throw unsupported(dataset ? "Only .csv, .xlsx and .json files are supported."
                : "Only .csv and .xlsx files are supported.");
    }

    private static boolean startsWithUtf16Bom(byte[] head) {
        return head.length >= 2 && (head[0] == (byte) 0xFF && head[1] == (byte) 0xFE
                || head[0] == (byte) 0xFE && head[1] == (byte) 0xFF);
    }

    /** The first character after a UTF-8 byte order mark and whitespace; 0 when the head holds none. */
    private static char firstNonBlank(byte[] head) {
        int start = head.length >= 3 && head[0] == (byte) 0xEF && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF
                ? 3 : 0;
        for (int i = start; i < head.length; i++) {
            char c = (char) (head[i] & 0xFF);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return 0;
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
