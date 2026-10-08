package com.hrms.cms.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code {{variable}}} substitution, shared by every template-backed document (staff
 * closure letters, citizen acknowledgement letters). Extracted out of
 * {@link CommunicationTemplateService} so a second template-backed feature doesn't need
 * its own copy of this regex loop.
 */
@Component
public class TemplateVariableRenderer {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /** Unresolved placeholders are left verbatim rather than blanked, so a missing variable is visible. */
    public String render(String templateText, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(templateText);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.getOrDefault(key, "{{" + key + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
