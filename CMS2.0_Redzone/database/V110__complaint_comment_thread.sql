-- V110: Threaded staff comments on a complaint, with PRIVATE / RESTRICTED / PUBLIC readership
-- MySQL version
--
-- Context: three partial comment-like surfaces already exist and NONE of them models readership.
-- COMPLAINT_QUERY_MESSAGE is the RE<->RBI correspondence thread (author_side RE|RBI, append-only per
-- UST857). COMPLAINT_INTERNAL_NOTE is flat, entity-side-only, and its service refuses any non-RE
-- caller. REASSIGNMENT_CLARIFICATION is scoped to a reassignment request. None of the three lets a
-- staff author choose an audience, so a staff-side note visible to a named few had nowhere to live.
-- All three are left untouched — their API shapes are pinned by E2E specs.
--
-- ═══ VISIBILITY IS NOT A CITIZEN-FACING SETTING ═══
-- PUBLIC means public-to-STAFF. The complainant has no readership of this table at ANY tier and no
-- endpoint serves it to a citizen session: ComplaintCommentService gates every path on an ALLOWLIST
-- of staff roles (a denylist would admit any identity nobody thought to name). The tier only ever
-- NARROWS the staff audience. Do not add a citizen-visible flag to this table; add a separate,
-- citizen-facing table if a citizen-visible remark is ever required.
--
-- restricted_to_roles follows the CLOSURE_CLAUSE_MASTER.restricted_to_roles precedent
-- (comma-separated, whole-token comparison in code — never a substring test, which would match
-- 'ADMIN' inside 'RBIO_ADMIN'). It differs in ONE respect, deliberately: there a blank column means
-- unrestricted, whereas a RESTRICTED comment with both lists blank stays visible to its AUTHOR ALONE,
-- because the author explicitly asked for narrow and failing open would publish it.
--
-- Threading is ONE level. parent_id references this table; the service refuses a reply whose parent
-- already has a parent. A reply INHERITS its parent's visibility and both restriction lists, so a
-- PUBLIC reply cannot leak a RESTRICTED parent's substance.
--
-- Re-running is safe: CREATE uses IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS COMPLAINT_COMMENT (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id          BIGINT       NOT NULL,

    -- NULL for a top-level comment. One level only; the service refuses a reply to a reply.
    parent_id             BIGINT       NULL,

    body                  TEXT         NOT NULL,

    -- PRIVATE | RESTRICTED | PUBLIC  -- PUBLIC is public-to-STAFF, never to the complainant
    visibility            VARCHAR(20)  NOT NULL,

    -- RESTRICTED only. Comma-separated, bare (unprefixed) Keycloak role names.
    restricted_to_roles   VARCHAR(1000) NULL,
    -- RESTRICTED only. Comma-separated user ids.
    restricted_to_user_ids VARCHAR(2000) NULL,

    author_user_id        VARCHAR(100) NOT NULL,
    author_name           VARCHAR(200) NOT NULL,
    author_role           VARCHAR(50)  NULL,

    -- DATETIME(6) so a thread ordered by createdAt is stable for comments posted in the same second,
    -- matching COMPLAINT_QUERY_MESSAGE.posted_at.
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NULL,
    -- An author may edit their own body (no time lock — these are working notes, not the
    -- tamper-evident record UST857 governs). There is NO delete path in the application; a retraction
    -- is an edit, and this counter records that one happened.
    edit_count            INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_ccm_complaint (complaint_id, created_at),
    KEY idx_ccm_parent (parent_id),
    KEY idx_ccm_author (author_user_id),
    CONSTRAINT fk_ccm_parent FOREIGN KEY (parent_id) REFERENCES COMPLAINT_COMMENT(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
