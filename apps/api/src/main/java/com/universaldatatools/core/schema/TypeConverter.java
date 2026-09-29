package com.universaldatatools.core.schema;

import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.transform.DatePatterns;
import com.universaldatatools.core.validate.EmailAddresses;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The only place a text value gets its field type (core-03 SR4, V0.1 design V3). Nothing is trimmed: a leading
 * space makes a value invalid, so tools that want trimming do it before. Thread-safe.
 */
public final class TypeConverter {

    /** ASCII digits only: other scripts' digits, signs, exponents and group separators are not numbers here. */
    private static final Pattern NUMBER = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    /** Longer is not a number anyone imports; parsing a million digits took 20 s, so it is refused first. */
    static final int NUMBER_MAX_LENGTH = 1000;
    /** Exactly four-digit years: the strict parser alone would also take +19900. */
    private static final Pattern ISO_DATE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    /** @param text not empty; an empty value never reaches conversion */
    public Converted convert(String text, SchemaField field) {
        return switch (field.type()) {
            case STRING -> new Converted.Ok(text);
            case NUMBER -> text.length() <= NUMBER_MAX_LENGTH && NUMBER.matcher(text).matches()
                    ? new Converted.Ok(new BigDecimal(text))
                    : new Converted.Failed(RowErrorCode.VALIDATION_TYPE, "Value is not a valid number.");
            case BOOLEAN -> bool(text);
            case DATE -> field.constraints().format() == null ? isoDate(text) : formatted(text, field.constraints().format());
            case EMAIL -> EmailAddresses.isValid(text)
                    ? new Converted.Ok(text)
                    : new Converted.Failed(RowErrorCode.VALIDATION_EMAIL, EmailAddresses.INVALID);
        };
    }

    private static Converted bool(String text) {
        // Lower-casing whole strings, not equalsIgnoreCase: that folds U+017F (long s) into S and accepts "fal\u017Fe".
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || text.equals("1")) {
            return new Converted.Ok(Boolean.TRUE);
        }
        if (lower.equals("false") || text.equals("0")) {
            return new Converted.Ok(Boolean.FALSE);
        }
        return new Converted.Failed(RowErrorCode.VALIDATION_TYPE, "Value is not a valid boolean (true/false/1/0).");
    }

    private static Converted isoDate(String text) {
        if (ISO_DATE.matcher(text).matches()) {
            LocalDate date = parse(text, ISO);
            if (date != null) {
                return new Converted.Ok(date);
            }
        }
        return new Converted.Failed(RowErrorCode.VALIDATION_TYPE, "Value is not a valid date (yyyy-MM-dd).");
    }

    /** The format was checked with the schema, so it compiles and needs no time of day. */
    private static Converted formatted(String text, String format) {
        LocalDate date = parse(text, DatePatterns.formatter(format));
        return date != null ? new Converted.Ok(date)
                : new Converted.Failed(RowErrorCode.VALIDATION_DATE_FORMAT, "Value is not a valid date (" + format + ").");
    }

    /** {@code null} for a day that does not exist or a year before 1. */
    private static LocalDate parse(String text, DateTimeFormatter formatter) {
        try {
            LocalDate date = LocalDate.parse(text, formatter);
            return date.getYear() >= 1 ? date : null;
        } catch (DateTimeException e) {
            return null;
        }
    }
}
