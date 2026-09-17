package com.rbi.cms.search.service;

import com.rbi.cms.common.exception.CmsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortFieldRegistryTest {

    private static final Pattern INDEXABLE_FIELD = Pattern.compile("^[A-Za-z0-9_.]+$");

    @Test
    @DisplayName("every mapped field name is plain ASCII, so a stray combining mark cannot hide in it")
    void mappedFieldNamesContainNoInvisibleCharacters() {
        for (Map.Entry<String, String> entry : SortFieldRegistry.mappings().entrySet()) {
            String field = entry.getValue();

            assertTrue(INDEXABLE_FIELD.matcher(field).matches(),
                    () -> "Sort key '" + entry.getKey() + "' maps to '" + field
                            + "', which contains characters that cannot appear in an index field name. "
                            + "Codepoints: " + field.codePoints().boxed().toList());
        }
    }

    @Test
    @DisplayName("an unknown sort key is rejected, not silently dropped")
    void unknownSortKeyIsRejected() {
        CmsException thrown = assertThrows(CmsException.class, () -> SortFieldRegistry.require("nonsense"));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
        assertTrue(thrown.getMessage().contains("nonsense"));
        assertTrue(thrown.getMessage().contains("complaintNumber"), "the message should list allowed keys");
    }

    @Test
    @DisplayName("slaBreachIn routes to the script sort and is not a translatable field")
    void slaBreachInIsAScriptSort() {
        assertTrue(SortFieldRegistry.isScriptSort(SortFieldRegistry.SLA_BREACH_IN));
        assertFalse(SortFieldRegistry.mappings().containsKey(SortFieldRegistry.SLA_BREACH_IN));
        assertTrue(SortFieldRegistry.allowed().contains(SortFieldRegistry.SLA_BREACH_IN),
                "it is still a sort key callers may send");
    }

    @Test
    @DisplayName("both complaint identifiers sort on the field every write path populates")
    void identifierSortsTargetTheBusinessComplaintNumber() {
        assertEquals("complaintNumber.keyword", SortFieldRegistry.require("complaintId"));
        assertEquals("complaintNumber.keyword", SortFieldRegistry.require("complaintNumber"));
    }

    @Test
    @DisplayName("mode sorts on filingType, which only works if the key is untainted")
    void modeSortsOnFilingType() {
        assertEquals("filingType.keyword", SortFieldRegistry.require("mode"));
    }
}
