package com.rbi.cms.search.service;

import com.rbi.cms.common.enums.ComplaintStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Single definition of the shape of a {@code cms-complaints} document.
 *
 * <p>Both write paths — the bulk reindex and the Kafka listener — must agree on the document id and
 * field names, or the same complaint is stored twice under different keys and the query layer reads
 * fields that only one path populates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintDocumentNormalizer {

    private final OfficerDirectoryService officerDirectory;

    public static final String FIELD_ID = "id";
    public static final String FIELD_COMPLAINT_NUMBER = "complaintNumber";
    public static final String FIELD_COMPLAINT_ID = "complaintId";
    public static final String FIELD_ASSIGNED_OFFICER = "assignedOfficer";
    public static final String FIELD_ASSIGNED_OFFICER_NAME = "assignedOfficerName";
    public static final String FIELD_DEPARTMENT = "department";
    public static final String FIELD_REGIONAL_OFFICE = "regionalOffice";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_STATUS = "status";
    /** Usernames of the officers who have opened the complaint; read state is per officer, not global. */
    public static final String FIELD_READ_BY = "readBy";

    /**
     * The OpenSearch {@code _id} is the business complaint identifier, not the database primary key.
     *
     * <p>That is the only identifier {@code ComplaintEvent} carries, so it is the only value a partial
     * update can address: {@code cms-backend}'s publisher sends {@code complaintNumber} in the event's
     * {@code complaintId}, and {@code cms-ingestion-service} sends its generated {@code CMP-…} id.
     * Keying on the numeric {@code id} instead would make every Kafka update miss the reindexed
     * document and create a second copy of it.
     */
    public Optional<String> resolveDocumentId(Map<String, Object> source) {
        if (source == null) {
            return Optional.empty();
        }
        return firstPresent(source, FIELD_COMPLAINT_NUMBER, FIELD_COMPLAINT_ID);
    }

    public Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, Object> doc = new HashMap<>(source);

        // Both write paths must expose the business identifier under one field name so it is sortable
        // and searchable for the whole corpus. The numeric `id` is deliberately left alone: the index
        // is dynamically mapped, so writing a string into a field the reindex path fills with a long
        // would be rejected as a mapping conflict.
        resolveDocumentId(source).ifPresent(id -> doc.put(FIELD_COMPLAINT_NUMBER, id));

        // The query layer filters and aggregates on assignedOfficer; upstream events call it assignedTo.
        if (!doc.containsKey(FIELD_ASSIGNED_OFFICER) && doc.get("assignedTo") != null) {
            doc.put(FIELD_ASSIGNED_OFFICER, doc.get("assignedTo"));
        }
        copyStringField(source, doc, FIELD_REGIONAL_OFFICE);
        copyStringField(source, doc, FIELD_CREATED_BY);
        if (source.get("hasAttachment") != null) {
            doc.put("hasAttachment", Boolean.valueOf(source.get("hasAttachment").toString()));
        }

        canonicalizeStatus(doc);
        enrichAssignedOfficerName(doc);

        // Date-only companions to the timestamp fields, so inline date filters can use an exact term.
        deriveDate(doc, "filedAt", "filedDate");
        deriveDate(doc, "createdAt", "createdDate");
        deriveDate(doc, "updatedAt", "updatedDate");

        return doc;
    }

    private void canonicalizeStatus(Map<String, Object> doc) {
        Object raw = doc.get(FIELD_STATUS);
        if (raw == null) {
            return;
        }
        String canonical = canonicalStatus(raw.toString());
        if (canonical != null) {
            doc.put(FIELD_STATUS, canonical);
        }
    }

    /**
     * The one spelling of a status that {@code status.keyword} is ever queried by.
     *
     * <p>The write paths disagree at source: the Kafka listener writes {@code name()}
     * ({@code IN_PROGRESS}) while the reindex copies {@code cms-backend}'s column verbatim, which is
     * lower snake_case ({@code in_progress}). Since {@code status.keyword} is matched with an exact
     * term, that divergence means a status filter returns only the half of the corpus written by
     * whichever path happened to match the caller's spelling.
     *
     * <p>Uppercase-underscore is the target form because it is what every reader already assumes:
     * the tab, KPI and aggregation clauses all compare against {@code ComplaintStatus.name()}.
     *
     * <p>The {@link ComplaintStatus} lookup runs first so display labels resolve to their constant
     * ({@code "Complaint Re Open"} → {@code COMPLAINT_REOPEN}, which mechanical word-splitting gets
     * wrong). Anything the enum does not know still gets normalized rather than passed through:
     * {@code ComplaintStatus} is not a complete inventory of what is stored — {@code pending},
     * {@code conciliated} and a dozen others have no constant — and leaving those in their original
     * case would put them in the index under a spelling no query can produce, which is how
     * {@code pending}, the {@code Complaint} entity's own default, would become unsearchable.
     *
     * <p>Callers on the query side must use this same method, or the two sides drift apart again.
     */
    public static String canonicalStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ComplaintStatus.parse(raw)
                .map(ComplaintStatus::name)
                .orElseGet(() -> raw.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    /**
     * Denormalizes the assigned officer's display name into the document, alongside — never instead of
     * — the username.
     *
     * <p>Done at write time rather than when projecting a response because the grid sorts and filters on
     * this column: resolving names per response would order and match on the username while displaying
     * something else. The username stays in {@code assignedOfficer} as the stable identity that the
     * scope and tab filters authorize against; display names are neither unique nor immutable.
     *
     * <p>Prefers the value already present in the source (the DB column carried by the reindex path and
     * the Kafka payload) so a Keycloak outage or directory-cache miss does not blank out a name that
     * the database already knows. Falls back to a Keycloak directory lookup when the source has no
     * value, and leaves the field unset when neither source can resolve it.
     */
    private void enrichAssignedOfficerName(Map<String, Object> doc) {
        Object existing = doc.get(FIELD_ASSIGNED_OFFICER_NAME);
        if (existing != null && !existing.toString().isBlank()) {
            doc.put(FIELD_ASSIGNED_OFFICER_NAME, existing.toString().strip());
            return;
        }

        Object assignedOfficer = doc.get(FIELD_ASSIGNED_OFFICER);
        if (assignedOfficer == null || assignedOfficer.toString().isBlank()) {
            return;
        }
        officerDirectory.displayNameFor(assignedOfficer.toString())
                .ifPresent(name -> doc.put(FIELD_ASSIGNED_OFFICER_NAME, name));
    }

    private void deriveDate(Map<String, Object> doc, String sourceKey, String targetKey) {
        String isoDate = toIsoDate(doc.get(sourceKey));
        if (isoDate != null) {
            doc.put(targetKey, isoDate);
        }
    }

    /**
     * Tolerates {@code 2026-09-16}, {@code 2026-09-16T10:23:45} and {@code 2026-09-16T10:23:45.123Z}.
     * Returns null rather than throwing, so one malformed record cannot abort a whole reindex page.
     */
    private String toIsoDate(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        int separator = text.indexOf('T');
        try {
            return LocalDate.parse(separator > 0 ? text.substring(0, separator) : text).toString();
        } catch (DateTimeParseException e) {
            log.debug("Unparseable date value '{}', leaving date-only field unset", text);
            return null;
        }
    }

    private static void copyStringField(Map<String, Object> source, Map<String, Object> doc, String key) {
        Object value = source.get(key);
        if (value != null && !value.toString().isBlank()) {
            doc.put(key, value.toString().strip());
        }
    }

    private Optional<String> firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return Optional.of(value.toString());
            }
        }
        return Optional.empty();
    }
}
