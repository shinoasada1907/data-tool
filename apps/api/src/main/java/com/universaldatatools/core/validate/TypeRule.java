package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks a value against its field type and converts it to the real type (design V3). The only place values are
 * converted; nothing is trimmed, so put a {@code trim} transformation first where needed.
 */
public final class TypeRule implements ValidationRule {

    /** ASCII digits only: other scripts' digits, signs, exponents and group separators are not numbers here. */
    private static final Pattern NUMBER = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    /** Longer is not a number anyone imports; parsing a million digits took 20 s, so it is refused first. */
    static final int NUMBER_MAX_LENGTH = 1000;
    /** Exactly four-digit years: the strict parser alone would also take +19900. */
    private static final Pattern ISO_DATE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    @Override
    public String type() {
        return "type";
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        String text = (String) value;
        return switch (context.fieldType()) {
            case STRING -> new ValidationResult.Valid(text);
            case NUMBER -> text.length() <= NUMBER_MAX_LENGTH && NUMBER.matcher(text).matches()
                    ? new ValidationResult.Valid(new BigDecimal(text))
                    : invalid(RowErrorCode.VALIDATION_TYPE, "Value is not a valid number.");
            case BOOLEAN -> bool(text);
            case DATE -> date(text);
            case EMAIL -> EmailAddresses.isValid(text)
                    ? new ValidationResult.Valid(text)
                    : invalid(RowErrorCode.VALIDATION_EMAIL, EmailAddresses.INVALID);
        };
    }

    private static ValidationResult bool(String text) {
        // Lower-casing whole strings, not equalsIgnoreCase: that folds U+017F (long s) into S and accepts "fal\u017Fe".
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || text.equals("1")) {
            return new ValidationResult.Valid(Boolean.TRUE);
        }
        if (lower.equals("false") || text.equals("0")) {
            return new ValidationResult.Valid(Boolean.FALSE);
        }
        return invalid(RowErrorCode.VALIDATION_TYPE, "Value is not a valid boolean (true/false/1/0).");
    }

    private static ValidationResult date(String text) {
        if (ISO_DATE.matcher(text).matches()) {
            try {
                LocalDate date = LocalDate.parse(text, DATE);
                if (date.getYear() >= 1) {
                    return new ValidationResult.Valid(date);
                }
            } catch (DateTimeException e) {
                // A day that does not exist: invalid, like any other malformed date.
            }
        }
        return invalid(RowErrorCode.VALIDATION_TYPE, "Value is not a valid date (yyyy-MM-dd).");
    }

    private static ValidationResult invalid(RowErrorCode code, String message) {
        return new ValidationResult.Invalid(code, message);
    }
}
