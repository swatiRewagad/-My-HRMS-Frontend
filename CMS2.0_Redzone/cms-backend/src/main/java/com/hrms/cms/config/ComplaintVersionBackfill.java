package com.hrms.cms.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sets {@code COMPLAINTS.record_version = 0} on rows where it is NULL.
 *
 * <p><b>Why this runs in Java and not only in the migration.</b> Adding the {@code @Version} column made
 * EVERY write to a pre-existing complaint fail:
 *
 * <pre>
 *   Null value was assigned to a property [Complaint.recordVersion] of primitive type (setter)
 * </pre>
 *
 * <p>Hibernate does not lazily initialise a null version — it reads the null and increments it. The
 * column must therefore be nullable (a NOT NULL default would be permanent for every other session on
 * this shared database) while never actually containing null.
 *
 * <p>V58/V56 do the backfill, but there is no Flyway in this tree: {@code database/*.sql} is hand-run and
 * schema comes from {@code ddl-auto: update}, which adds the column WITHOUT running the UPDATE. So a
 * developer who pulls this branch and starts the app gets the column and no backfill, and every RBIO
 * action 400s on existing data. This closes that gap on every boot.
 *
 * <p>Idempotent and effectively free once clean: the WHERE clause matches nothing, so it is a single
 * indexed-scan UPDATE affecting 0 rows. Runs at {@code @Order(22)}, before the RBIO seeders' own work
 * matters and well before any request is served.
 */
@Component
@Order(22)
@RequiredArgsConstructor
@Slf4j
public class ComplaintVersionBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    /**
     * The transaction is managed here rather than with {@code @Transactional} on {@link #run}, so that the
     * catch below sits OUTSIDE the transaction boundary.
     *
     * <p>With the annotation the "never block startup" promise could not be kept: a failing statement marks
     * the transaction rollback-only inside Hibernate, so swallowing the exception merely deferred it to the
     * commit, which then threw {@code UnexpectedRollbackException} from the runner and aborted the boot —
     * exactly the outcome the catch exists to prevent. Committing (or rolling back) before catching is what
     * actually makes the failure non-fatal.
     */
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer updated = transactionTemplate.execute(status -> entityManager
                    // Lowercase and unquoted on purpose. Complaint declares @Table(name = "COMPLAINTS"), but
                    // Boot's CamelCaseToUnderscoresNamingStrategy folds that to `complaints`, so the literal
                    // uppercase name matched no table on a case-sensitive server (Linux MySQL defaults to
                    // lower_case_table_names=0) and this backfill silently never ran there. Unquoted
                    // lowercase resolves on MySQL either way and on Oracle, which folds it to uppercase.
                    .createNativeQuery("UPDATE complaints SET record_version = 0 WHERE record_version IS NULL")
                    .executeUpdate());
            if (updated != null && updated > 0) {
                log.info("Backfilled record_version=0 on {} complaint(s) that predate optimistic locking", updated);
            }
        } catch (Exception e) {
            // Never block startup. A failure here means writes to legacy rows will fail loudly at the
            // point of use, which is far easier to diagnose than an application that refuses to boot.
            log.warn("Could not backfill complaints.record_version: {}", e.getMessage());
        }
    }
}
