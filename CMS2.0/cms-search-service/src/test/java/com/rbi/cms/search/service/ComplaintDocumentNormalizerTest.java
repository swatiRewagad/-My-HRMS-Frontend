package com.rbi.cms.search.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The normalizer is the only seam the bulk reindex and the Kafka listener share, so anything it fails
 * to unify becomes a half-populated corpus: the same complaint stored under two spellings, and filters
 * that match whichever half the caller happened to guess.
 */
class ComplaintDocumentNormalizerTest {

    private OfficerDirectoryService officerDirectory;
    private ComplaintDocumentNormalizer normalizer;

    @BeforeEach
    void setUp() {
        officerDirectory = mock(OfficerDirectoryService.class);
        when(officerDirectory.displayNameFor(anyString())).thenReturn(Optional.empty());
        normalizer = new ComplaintDocumentNormalizer(officerDirectory);
    }

    private String statusAfterNormalize(String raw) {
        Map<String, Object> source = new HashMap<>();
        source.put("status", raw);
        return (String) normalizer.normalize(source).get("status");
    }

    @Nested
    @DisplayName("status canonicalization")
    class Status {

        // The reason this class exists. status.keyword is matched with an unanalyzed term, so the value
        // the indexer writes and the value the query builds have to be character-identical.
        @Test
        @DisplayName("every spelling of a status converges on one indexed value")
        void spellingsConverge() {
            assertThat(statusAfterNormalize("in_progress")).isEqualTo("IN_PROGRESS");
            assertThat(statusAfterNormalize("IN_PROGRESS")).isEqualTo("IN_PROGRESS");
            assertThat(statusAfterNormalize("In Progress")).isEqualTo("IN_PROGRESS");
            assertThat(statusAfterNormalize("  in progress  ")).isEqualTo("IN_PROGRESS");
        }

        // The two write paths as they actually behave: cms-backend's column is lower snake_case, the
        // Kafka listener writes ComplaintStatus.name(). Before canonicalization a status filter saw
        // only one of them.
        @Test
        @DisplayName("the reindex and Kafka spellings of the same status agree after normalizing")
        void writePathsAgree() {
            assertThat(statusAfterNormalize("sent_back")).isEqualTo(statusAfterNormalize("SENT_BACK"));
        }

        // Mechanical word-splitting gets this one wrong: the label has a space that the constant does
        // not, so "Complaint Re Open" would become COMPLAINT_RE_OPEN without the enum lookup.
        @Test
        @DisplayName("a display label resolves to its constant, not to its word-split form")
        void labelResolvesToConstant() {
            assertThat(statusAfterNormalize("Complaint Re Open")).isEqualTo("COMPLAINT_REOPEN");
        }

        // ComplaintStatus is not a complete inventory of what cms-backend stores. If unknown values
        // were passed through unchanged they would sit in the index in a case no query produces —
        // "pending" is the Complaint entity's own default, so that is most of the corpus.
        @Test
        @DisplayName("a status with no enum constant is still normalized, not passed through")
        void unknownStatusIsNormalized() {
            assertThat(statusAfterNormalize("pending")).isEqualTo("PENDING");
            assertThat(statusAfterNormalize("conciliated")).isEqualTo("CONCILIATED");
            assertThat(statusAfterNormalize("awaiting_closure")).isEqualTo("AWAITING_CLOSURE");
            assertThat(statusAfterNormalize("info requested")).isEqualTo("INFO_REQUESTED");
        }

        @Test
        @DisplayName("an absent or blank status is left alone rather than invented")
        void absentStatusIsLeftAlone() {
            assertThat(normalizer.normalize(new HashMap<>())).doesNotContainKey("status");
            assertThat(statusAfterNormalize("   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("canonicalStatus is null-safe for the query side")
        void canonicalStatusIsNullSafe() {
            assertThat(ComplaintDocumentNormalizer.canonicalStatus(null)).isNull();
            assertThat(ComplaintDocumentNormalizer.canonicalStatus("")).isNull();
            assertThat(ComplaintDocumentNormalizer.canonicalStatus("  ")).isNull();
        }
    }

    @Nested
    @DisplayName("assigned officer")
    class AssignedOfficer {

        @Test
        @DisplayName("a resolved display name is added alongside the username, never instead of it")
        void displayNameIsAdditive() {
            when(officerDirectory.displayNameFor("rbio.officer1")).thenReturn(Optional.of("Asha Menon"));

            Map<String, Object> source = new HashMap<>();
            source.put("assignedOfficer", "rbio.officer1");

            Map<String, Object> doc = normalizer.normalize(source);

            assertThat(doc.get("assignedOfficerName")).isEqualTo("Asha Menon");
            assertThat(doc.get("assignedOfficer")).isEqualTo("rbio.officer1");
        }

        // Left unset rather than filled with a placeholder, so the projection falls back to the
        // username. A username is actionable; "Unknown" is not.
        @Test
        @DisplayName("an unresolvable officer leaves the name field unset")
        void unresolvedNameIsOmitted() {
            Map<String, Object> source = new HashMap<>();
            source.put("assignedOfficer", "ghost.user");

            Map<String, Object> doc = normalizer.normalize(source);

            assertThat(doc).doesNotContainKey("assignedOfficerName");
            assertThat(doc.get("assignedOfficer")).isEqualTo("ghost.user");
        }

        // Upstream events name the field assignedTo; the query layer filters and aggregates on
        // assignedOfficer.
        @Test
        @DisplayName("assignedTo is aliased onto assignedOfficer")
        void assignedToIsAliased() {
            when(officerDirectory.displayNameFor("rbio.officer2")).thenReturn(Optional.of("Ravi Iyer"));

            Map<String, Object> source = new HashMap<>();
            source.put("assignedTo", "rbio.officer2");

            Map<String, Object> doc = normalizer.normalize(source);

            assertThat(doc.get("assignedOfficer")).isEqualTo("rbio.officer2");
            assertThat(doc.get("assignedOfficerName")).isEqualTo("Ravi Iyer");
        }

        @Test
        @DisplayName("a blank officer is not looked up")
        void blankOfficerIsSkipped() {
            Map<String, Object> source = new HashMap<>();
            source.put("assignedOfficer", "  ");

            assertThat(normalizer.normalize(source)).doesNotContainKey("assignedOfficerName");
        }
    }

    @Nested
    @DisplayName("date-only companions")
    class Dates {

        // These are the fields the inline date filters target. A term on the underlying timestamp
        // compares instants, so a date-only value would parse to midnight and match nothing.
        @Test
        @DisplayName("timestamps yield date-only companion fields")
        void companionsAreDerived() {
            Map<String, Object> source = new HashMap<>();
            source.put("createdAt", "2026-09-16T10:23:45.123Z");
            source.put("updatedAt", "2026-09-15T08:00:00");
            source.put("filedAt", "2026-09-14");

            Map<String, Object> doc = normalizer.normalize(source);

            assertThat(doc.get("createdDate")).isEqualTo("2026-09-16");
            assertThat(doc.get("updatedDate")).isEqualTo("2026-09-15");
            assertThat(doc.get("filedDate")).isEqualTo("2026-09-14");
        }

        // One malformed record must not abort a whole reindex page.
        @Test
        @DisplayName("an unparseable timestamp leaves the companion unset instead of throwing")
        void malformedTimestampIsTolerated() {
            Map<String, Object> source = new HashMap<>();
            source.put("createdAt", "not-a-date");

            assertThat(normalizer.normalize(source)).doesNotContainKey("createdDate");
        }
    }

    @Nested
    @DisplayName("document id")
    class DocumentId {

        // Unifying _id across the two write paths is what makes a backfill reindex a repair rather
        // than a second copy of the corpus.
        @Test
        @DisplayName("complaintNumber wins over complaintId")
        void complaintNumberPreferred() {
            Map<String, Object> source = new HashMap<>();
            source.put("complaintNumber", "CMP-2026-001");
            source.put("complaintId", "CMP-GENERATED-9");

            assertThat(normalizer.resolveDocumentId(source)).contains("CMP-2026-001");
        }

        @Test
        @DisplayName("complaintId is the fallback and is copied onto complaintNumber")
        void complaintIdFallback() {
            Map<String, Object> source = new HashMap<>();
            source.put("complaintId", "CMP-GENERATED-9");

            assertThat(normalizer.resolveDocumentId(source)).contains("CMP-GENERATED-9");
            assertThat(normalizer.normalize(source).get("complaintNumber")).isEqualTo("CMP-GENERATED-9");
        }

        @Test
        @DisplayName("neither present, or blank, yields no id")
        void noIdentifier() {
            assertThat(normalizer.resolveDocumentId(new HashMap<>())).isEmpty();
            assertThat(normalizer.resolveDocumentId(null)).isEmpty();

            Map<String, Object> blank = new HashMap<>();
            blank.put("complaintNumber", "  ");
            assertThat(normalizer.resolveDocumentId(blank)).isEmpty();
        }
    }
}
