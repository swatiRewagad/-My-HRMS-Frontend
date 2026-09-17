package com.rbi.cms.search.validation;

import com.rbi.cms.common.enums.LabeledEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private Set<String> constantNames;
    private Set<String> labels;
    private String allowedDescription;

    @Override
    public void initialize(EnumValue annotation) {
        Enum<?>[] constants = annotation.value().getEnumConstants();

        constantNames = Arrays.stream(constants)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        labels = Arrays.stream(constants)
                .filter(LabeledEnum.class::isInstance)
                .map(constant -> ((LabeledEnum) constant).getValue())
                .filter(label -> label != null && !label.isBlank())
                .map(EnumValueValidator::upper)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        allowedDescription = String.join(", ", new TreeSet<>(constantNames));
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Optional filter: absent and cleared both mean "do not filter on this column".
        if (value == null || value.isBlank()) {
            return true;
        }

        if (constantNames.contains(normalize(value)) || labels.contains(upper(value))) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must be one of: " + allowedDescription)
                .addConstraintViolation();
        return false;
    }

    /**
     * Mirrors the query layer's own status normalization, so the API accepts exactly the set of
     * inputs that would have produced a matching filter and rejects exactly those that would not.
     */
    private static String normalize(String value) {
        return upper(value).replace(' ', '_');
    }

    private static String upper(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
