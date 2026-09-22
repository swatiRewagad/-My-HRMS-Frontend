package com.rbi.cms.notification.repository;

import com.rbi.cms.common.enums.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the conditional UPDATE that stands in for a state machine, against a real database.
 */
@JdbcTest
@Import(OutboundEmailRepository.class)
@TestPropertySource(properties = {
        "spring.sql.init.schema-locations=classpath:schema-simulated-emails.sql",
        "spring.datasource.url=jdbc:h2:mem:outbound;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class OutboundEmailRepositoryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 2, 10, 0);

    @Autowired private OutboundEmailRepository repository;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM SIMULATED_EMAILS").update();
    }

    private long insert(String deliveryStatus) {
        jdbcClient.sql("""
                        INSERT INTO SIMULATED_EMAILS
                            (MESSAGE_ID, FROM_EMAIL, TO_EMAIL, CC_EMAIL, BCC_EMAIL, SUBJECT, BODY,
                             DIRECTION, STATUS, DELIVERY_STATUS, COMPLAINT_NUMBER, SENT_AT, UPDATED_AT)
                        VALUES ('msg-1', 'officer@rbi.org.in', 'nodal@bank.example.com', 'cc@rbi.org.in',
                                'bcc@rbi.org.in', 'Comments called for', '<p>Please respond.</p>',
                                'OUTBOUND', :status, :status, 'CMS-20260101-ABC123', :createdAt, :createdAt)
                        """)
                .param("status", deliveryStatus)
                .param("createdAt", CREATED_AT)
                .update();
        return jdbcClient.sql("SELECT MAX(ID) FROM SIMULATED_EMAILS").query(Long.class).single();
    }

    private Map<String, Object> row(long id) {
        return jdbcClient.sql("SELECT * FROM SIMULATED_EMAILS WHERE ID = :id")
                .param("id", id)
                .query()
                .singleRow();
    }

    @Test
    void shouldReadTheColumnsNeededToDispatch() {
        long id = insert(DeliveryStatus.PENDING.name());

        Optional<OutboundEmailRow> found = repository.find(id);

        assertThat(found).isPresent();
        OutboundEmailRow email = found.orElseThrow();
        assertThat(email.getToEmail()).isEqualTo("nodal@bank.example.com");
        assertThat(email.getCcEmail()).isEqualTo("cc@rbi.org.in");
        assertThat(email.getBccEmail()).isEqualTo("bcc@rbi.org.in");
        assertThat(email.getBody()).contains("Please respond");
        assertThat(email.getDeliveryStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldReturnEmptyForAMissingRow() {
        assertThat(repository.find(999L)).isEmpty();
    }

    @Test
    void shouldTransitionAPendingRowToSent() {
        long id = insert(DeliveryStatus.PENDING.name());

        int updated = repository.markSent(id, LocalDateTime.of(2026, 1, 3, 9, 0));

        assertThat(updated).isEqualTo(1);
        Map<String, Object> row = row(id);
        assertThat(row.get("DELIVERY_STATUS")).isEqualTo("SENT");
        assertThat(row.get("STATUS")).isEqualTo("SENT");
        assertThat(row.get("PROCESSED_AT")).isNotNull();
        assertThat(row.get("LAST_ERROR")).isNull();
    }

    /** The idempotency contract: a redelivered event finds nothing to do rather than resolving twice. */
    @Test
    void shouldNotTransitionARowThatIsNoLongerPending() {
        long id = insert(DeliveryStatus.SENT.name());

        assertThat(repository.markSent(id, LocalDateTime.now())).isZero();
        assertThat(repository.markFailed(id, "boom", LocalDateTime.now())).isZero();
    }

    @Test
    void shouldRecordAFailureAndCountTheAttempt() {
        long id = insert(DeliveryStatus.PENDING.name());

        int updated = repository.markFailed(id, "SMTP refused the connection", LocalDateTime.now());

        assertThat(updated).isEqualTo(1);
        Map<String, Object> row = row(id);
        assertThat(row.get("DELIVERY_STATUS")).isEqualTo("FAILED");
        assertThat(row.get("LAST_ERROR")).isEqualTo("SMTP refused the connection");
        // COALESCE, because the column starts null rather than zero.
        assertThat(((Number) row.get("DISPATCH_ATTEMPTS")).intValue()).isEqualTo(1);
    }

    @Test
    void shouldTruncateAnOverlongError() {
        long id = insert(DeliveryStatus.PENDING.name());

        repository.markFailed(id, "x".repeat(5000), LocalDateTime.now());

        assertThat((String) row(id).get("LAST_ERROR")).hasSize(2000);
    }

    /**
     * Writing through JDBC bypasses the entity's @PreUpdate, so the repository has to move UPDATED_AT itself
     * - and must leave SENT_AT, the activity list's sort key, exactly where it was.
     */
    @Test
    void shouldAdvanceUpdatedAtButNeverTouchSentAt() {
        long id = insert(DeliveryStatus.PENDING.name());
        LocalDateTime dispatchedAt = LocalDateTime.of(2026, 2, 1, 12, 0);

        repository.markSent(id, dispatchedAt);

        Map<String, Object> row = row(id);
        assertThat(((java.sql.Timestamp) row.get("UPDATED_AT")).toLocalDateTime()).isEqualTo(dispatchedAt);
        assertThat(((java.sql.Timestamp) row.get("SENT_AT")).toLocalDateTime()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldPassTheSchemaProbeWhenTheDeliveryColumnsExist() {
        repository.assertSchema();
    }

    @Test
    void shouldFailTheSchemaProbeWhenADeliveryColumnIsMissing() {
        // The in-memory database is shared across this class, so the column must go back or every later
        // test inherits a broken schema.
        jdbcClient.sql("ALTER TABLE SIMULATED_EMAILS DROP COLUMN DELIVERY_STATUS").update();
        try {
            assertThatThrownBy(() -> repository.assertSchema()).isInstanceOf(Exception.class);
        } finally {
            jdbcClient.sql("ALTER TABLE SIMULATED_EMAILS ADD COLUMN DELIVERY_STATUS VARCHAR(20)").update();
        }
    }
}
