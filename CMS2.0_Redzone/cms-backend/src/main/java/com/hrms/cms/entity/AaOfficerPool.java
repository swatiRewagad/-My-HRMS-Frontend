package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An officer available to receive AA work.
 *
 * Deliberately maps the EXISTING WF_OFFICER_POOL table rather than introducing a parallel AA pool.
 * The table already carries active / on-leave / threshold state and is populated (17 rows), and it
 * lives in the same schema as cms-backend even though it was created by cms-workflow-service. A second
 * pool table would have to be kept in step with the first by hand.
 *
 * cms-workflow-service maps the same table with its own entity. Two mappings in two JVMs are fine;
 * what matters is that the column names here match exactly and that nothing already in use is
 * re-typed. SKILL_LANGUAGES is the only column this module adds.
 */
@Entity
@Table(name = "wf_officer_pool")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaOfficerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "display_name")
    private String displayName;

    /** Pool this officer belongs to, e.g. "AA_DO". Also the round-robin pointer key. */
    @Column(name = "role_group", nullable = false)
    private String roleGroup;

    @Column(name = "regional_office")
    private String regionalOffice;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_on_leave", nullable = false)
    private boolean onLeave;

    /**
     * Counter maintained by cms-workflow-service. Read for display only.
     *
     * NOT used for eligibility. It is decremented elsewhere using a role name where a user id is
     * expected, so it drifts from reality; the engine counts live rows instead.
     *
     * Explicitly non-writable. A full-row UPDATE from this module (saving a threshold or activation
     * change) would otherwise overwrite an increment cms-workflow-service made in the meantime, since
     * the two run in separate JVMs against this shared table.
     */
    @Column(name = "current_workload", insertable = false, updatable = false)
    private Integer currentWorkload;

    /** Per-officer threshold. 0 (or null) means unlimited, matching the existing convention. */
    @Column(name = "max_workload")
    private Integer maxWorkload;

    /**
     * Comma-separated ISO language codes this officer can handle, e.g. "bn,hi".
     *
     * CSV rather than a join table to stay consistent with how multi-valued config is already stored
     * and read (SystemConfigService.getSet). A pool of this size does not justify a join.
     */
    @Column(name = "skill_languages", length = 200)
    private String skillLanguages;

    /** Null and 0 both mean unlimited. */
    public boolean hasUnlimitedThreshold() {
        return maxWorkload == null || maxWorkload <= 0;
    }

    public int thresholdOrZero() {
        return maxWorkload == null ? 0 : maxWorkload;
    }

    /** True when this officer is listed as able to handle {@code language}. */
    public boolean speaks(String language) {
        if (language == null || language.isBlank() || skillLanguages == null) {
            return false;
        }
        String wanted = language.trim().toLowerCase();
        for (String code : skillLanguages.split(",")) {
            if (code.trim().toLowerCase().equals(wanted)) {
                return true;
            }
        }
        return false;
    }
}
