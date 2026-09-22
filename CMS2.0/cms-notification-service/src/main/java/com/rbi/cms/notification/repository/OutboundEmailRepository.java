package com.rbi.cms.notification.repository;

import com.rbi.cms.common.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reads and resolves one outbound mail in {@code SIMULATED_EMAILS}, a table owned by cms-backend.
 *
 * <p><strong>The WHERE clause is the state machine.</strong> Both writes are conditional on the row still
 * being PENDING, and both return the affected row count. A count of zero means somebody already resolved
 * it, which is exactly what a Kafka redelivery looks like — so at-least-once delivery becomes
 * effectively-once without a dedupe table, and a rebalanced consumer racing a manual retry cannot
 * double-resolve.</p>
 *
 * <p>Every statement sets {@code UPDATED_AT} explicitly. Writing through JDBC bypasses the entity's
 * {@code @PreUpdate} callback, so nothing else would move it, and a stale {@code UPDATED_AT} would break
 * both the timestamp the officer sees and the operational query for mail stuck in PENDING.</p>
 *
 * <p>{@code SENT_AT} is never touched: it is the row's creation time and the activity list's sort key.
 * A completed dispatch is recorded in {@code PROCESSED_AT}.</p>
 */
@Repository
@RequiredArgsConstructor
public class OutboundEmailRepository {

    private final JdbcClient jdbcClient;

    private static final String SELECT_SQL = """
            SELECT ID, MESSAGE_ID, FROM_EMAIL, TO_EMAIL, CC_EMAIL, BCC_EMAIL,
                   SUBJECT, BODY, COMPLAINT_NUMBER, DELIVERY_STATUS
              FROM SIMULATED_EMAILS
             WHERE ID = :id
            """;

    private static final String MARK_SENT_SQL = """
            UPDATE SIMULATED_EMAILS
               SET DELIVERY_STATUS = :sent,
                   STATUS = :sent,
                   PROCESSED_AT = :now,
                   UPDATED_AT = :now,
                   LAST_ERROR = NULL
             WHERE ID = :id
               AND DELIVERY_STATUS = :pending
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE SIMULATED_EMAILS
               SET DELIVERY_STATUS = :failed,
                   STATUS = :failed,
                   LAST_ERROR = :error,
                   UPDATED_AT = :now,
                   DISPATCH_ATTEMPTS = COALESCE(DISPATCH_ATTEMPTS, 0) + 1
             WHERE ID = :id
               AND DELIVERY_STATUS = :pending
            """;

    /** Probes the columns this service depends on without reading any row. */
    private static final String SCHEMA_PROBE_SQL = """
            SELECT DELIVERY_STATUS, LAST_ERROR, DISPATCH_ATTEMPTS, CC_EMAIL, BCC_EMAIL, UPDATED_AT
              FROM SIMULATED_EMAILS
             WHERE 1 = 0
            """;

    public Optional<OutboundEmailRow> find(long id) {
        return jdbcClient.sql(SELECT_SQL)
                .param("id", id)
                .query((rs, rowNum) -> OutboundEmailRow.builder()
                        .id(rs.getLong("ID"))
                        .messageId(rs.getString("MESSAGE_ID"))
                        .fromEmail(rs.getString("FROM_EMAIL"))
                        .toEmail(rs.getString("TO_EMAIL"))
                        .ccEmail(rs.getString("CC_EMAIL"))
                        .bccEmail(rs.getString("BCC_EMAIL"))
                        .subject(rs.getString("SUBJECT"))
                        .body(rs.getString("BODY"))
                        .complaintNumber(rs.getString("COMPLAINT_NUMBER"))
                        .deliveryStatus(rs.getString("DELIVERY_STATUS"))
                        .build())
                .optional();
    }

    /** @return 1 when this call performed the transition, 0 when the row was no longer PENDING. */
    public int markSent(long id, LocalDateTime now) {
        return jdbcClient.sql(MARK_SENT_SQL)
                .param("sent", DeliveryStatus.SENT.name())
                .param("pending", DeliveryStatus.PENDING.name())
                .param("now", now)
                .param("id", id)
                .update();
    }

    /** @return 1 when this call performed the transition, 0 when the row was no longer PENDING. */
    public int markFailed(long id, String error, LocalDateTime now) {
        return jdbcClient.sql(MARK_FAILED_SQL)
                .param("failed", DeliveryStatus.FAILED.name())
                .param("pending", DeliveryStatus.PENDING.name())
                .param("error", truncate(error, 2000))
                .param("now", now)
                .param("id", id)
                .update();
    }

    /**
     * Fails if the delivery columns are absent. There is no migration tool in this repo, so nothing
     * guarantees V28/V29 were applied; without this the first dispatch would fail with an opaque SQL error
     * and land in the dead-letter topic instead of telling anyone what is actually wrong.
     */
    public void assertSchema() {
        jdbcClient.sql(SCHEMA_PROBE_SQL).query().listOfRows();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
