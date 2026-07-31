package com.rbi.cms.common.enums;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class ComplaintStatusDeserializer extends JsonDeserializer<ComplaintStatus> {

    @Override
    public ComplaintStatus deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ComplaintStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ComplaintStatus.NEW;
        }
    }
}
