package com.universalimporter.domain.transformation;

import com.universalimporter.domain.common.TextValues;
import com.universalimporter.domain.schema.FieldType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads a date with {@code params.inputFormat} and writes it with {@code params.outputFormat}, ISO by default
 * (design T3). Parsing is strict: a day that does not exist does not match. The value is not trimmed first; put a
 * {@code trim} step before this one.
 */
public final class DateFormatTransformation implements Transformation {

    static final String TYPE = "dateFormat";
    static final String INPUT_FORMAT = "inputFormat";
    static final String OUTPUT_FORMAT = "outputFormat";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<String> validate(TransformationContext context) {
        List<String> problems = new ArrayList<>(
                TransformationParams.unknown(context, Set.of(INPUT_FORMAT, OUTPUT_FORMAT), type()));
        List<String> input = TransformationParams.required(context, INPUT_FORMAT);
        problems.addAll(input);
        if (input.isEmpty()) {
            DatePatterns.checkInput(context.params().get(INPUT_FORMAT)).ifPresent(problems::add);
        }
        String output = context.params().get(OUTPUT_FORMAT);
        if (output != null) {
            List<String> outputProblems = new ArrayList<>(TransformationParams.required(context, OUTPUT_FORMAT));
            if (outputProblems.isEmpty()) {
                DatePatterns.checkOutput(output).ifPresent(outputProblems::add);
            }
            if (outputProblems.isEmpty() && context.fieldType() == FieldType.DATE && !DatePatterns.isIso(output)) {
                outputProblems.add("Fields of type date must output " + DatePatterns.ISO + ".");
            }
            problems.addAll(outputProblems);
        }
        return problems;
    }

    @Override
    public String transform(String value, TransformationContext context) throws TransformationFailure {
        if (TextValues.isEmpty(value)) {
            return value;
        }
        String input = context.params().get(INPUT_FORMAT);
        LocalDate date;
        try {
            date = LocalDate.parse(value, DatePatterns.formatter(input));
        } catch (DateTimeParseException e) {
            // Only the pattern the user wrote: the cell value must not reach messages (design D13).
            throw new TransformationFailure("Value does not match pattern " + input);
        }
        return DatePatterns.formatter(outputFormat(context)).format(date);
    }

    /** The configured output format, or ISO when none was given. */
    static String outputFormat(TransformationContext context) {
        return context.params().getOrDefault(OUTPUT_FORMAT, DatePatterns.ISO);
    }
}
