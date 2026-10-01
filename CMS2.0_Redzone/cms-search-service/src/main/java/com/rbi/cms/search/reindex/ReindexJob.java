package com.rbi.cms.search.reindex;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.rbi.cms.search.config.SearchProperties;
import com.rbi.cms.search.index.ComplaintDocument;
import com.rbi.cms.search.index.ComplaintIndexManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full rebuild of the complaint index.
 *
 * <p>Reads from the database directly rather than over HTTP from {@code cms-ingestion}. The service
 * had no datasource before, so this adds one, and that is the smaller cost of the two options:
 *
 * <p>The HTTP route is what the previous implementation attempted, and it could not work. There is
 * no paged "list all complaints" endpoint to call — the old code walked
 * {@code data.recentComplaints} off a dashboard-stats response, which is a short unpaginated display
 * list, so a "reindex all" over 2193 complaints indexed whatever handful the dashboard happened to
 * show. Building the missing paged admin endpoint would mean a second service's API surface, its own
 * auth, and one HTTP round trip per complaint for the timeline and appeal joins. Reading the tables
 * directly is one connection, keyset-paged, and the joins happen in the database where they belong.
 *
 * <p>The trade-off accepted: this service now knows the COMPLAINTS/COMPLAINT_TIMELINE/APPEAL_ORDER
 * column names, so a schema change there can break a reindex here. That is why the SELECT lists
 * columns explicitly instead of {@code SELECT *} — a dropped column fails loudly on the next
 * reindex rather than silently indexing nulls.
 *
 * <p>Safe to kill: the alias is not moved until the new index is fully built, so an interrupted run
 * leaves the live alias untouched and only orphans a partial index.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "cms.search.enabled", havingValue = "true", matchIfMissing = true)
public class ReindexJob {

    /**
     * Keyset cursor, not OFFSET. With OFFSET the database re-walks and discards every row before the
     * offset on each batch, so batch N costs O(N x batchSize) and a reindex gets quadratically slower
     * as it proceeds. It is also incorrect under concurrent writes: a row inserted or deleted below
     * the cursor shifts every later row's position, so pages silently skip or duplicate complaints.
     */
    private static final String COMPLAINT_PAGE_SQL = """
            SELECT id, complaint_number, subject, description, advisory_text, closure_clause,
                   withdrawal_reason, scheme_coverage_reason, reopen_reason, reopen_justification,
                   category_id, entity_code, department, status, workflow_stage, milestone,
                   rbio_office_code, ground_of_complaint_id, award_amount, compensation_type,
                   maintainability_determination, priority, assigned_officer, assigned_role,
                   created_at, updated_at
            FROM COMPLAINTS
            WHERE id > ?
            ORDER BY id ASC
            LIMIT ?
            """;

    private static final String TIMELINE_SQL = """
            SELECT complaint_id, action, from_status, to_status, performed_by_role, performed_at, remarks
            FROM COMPLAINT_TIMELINE
            WHERE complaint_id BETWEEN ? AND ?
              AND remarks IS NOT NULL AND remarks <> ''
            ORDER BY complaint_id ASC, performed_at ASC
            """;

    private static final String APPEAL_ORDER_SQL = """
            SELECT a.original_complaint_number AS complaint_number, ao.appeal_number, ao.clause_code,
                   ao.outcome, ao.order_summary, ao.ground, ao.correction_reason
            FROM APPEAL_ORDER ao
            JOIN appeals a ON a.appeal_number = ao.appeal_number
            WHERE a.original_complaint_number IN (%s)
              AND ao.superseded_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ElasticsearchClient bulkClient;
    private final ComplaintIndexManager indexManager;
    private final ComplaintRowMapper rowMapper;
    private final SearchProperties properties;

    /**
     * Guards against two concurrent reindexes. Two runs would each build their own index and then
     * race on the alias swap, and the loser's index would be left orphaned while the alias points at
     * whichever finished last.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private volatile ReindexStatus lastStatus = ReindexStatus.idle();

    public ReindexJob(JdbcTemplate jdbcTemplate,
                      @Qualifier("bulkElasticsearchClient") ElasticsearchClient bulkClient,
                      ComplaintIndexManager indexManager,
                      ComplaintRowMapper rowMapper,
                      SearchProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.bulkClient = bulkClient;
        this.indexManager = indexManager;
        this.rowMapper = rowMapper;
        this.properties = properties;
    }

    public ReindexStatus run() {
        if (!running.compareAndSet(false, true)) {
            return lastStatus.withMessage("A reindex is already running");
        }

        String newIndex = null;
        long indexed = 0;
        long failed = 0;
        long cursor = 0;
        long started = System.currentTimeMillis();

        try {
            newIndex = indexManager.createVersionedIndex();
            indexManager.suspendRefresh(newIndex);

            int batchSize = properties.getReindex().getBatchSize();
            long maxDocuments = properties.getReindex().getMaxDocuments();

            while (true) {
                List<ComplaintDocument> batch = loadBatch(cursor, batchSize);
                if (batch.isEmpty()) {
                    break;
                }

                attachTimeline(batch);
                attachAppealOrders(batch);

                BatchResult result = bulkWithRetry(newIndex, batch);
                indexed += result.succeeded();
                failed += result.failed();

                cursor = Long.parseLong(batch.get(batch.size() - 1).getComplaintId());

                if (maxDocuments > 0 && indexed >= maxDocuments) {
                    log.info("Reindex stopping at configured cap of {} documents", maxDocuments);
                    break;
                }

                pause();
            }

            indexManager.restoreRefreshInterval(newIndex);
            List<String> detached = indexManager.swapAliasTo(newIndex);

            for (String old : detached) {
                if (!old.equals(newIndex)) {
                    try {
                        indexManager.deleteIndex(old);
                    } catch (Exception e) {
                        log.warn("Could not delete superseded index {}: {}", old, e.getMessage());
                    }
                }
            }

            lastStatus = new ReindexStatus(true, newIndex, indexed, failed, cursor,
                    System.currentTimeMillis() - started, "Reindex completed");
            log.info("Reindex complete: index={}, indexed={}, failed={}", newIndex, indexed, failed);

        } catch (Exception e) {
            log.error("Reindex aborted after {} documents (cursor={}): {}", indexed, cursor, e.getMessage(), e);
            lastStatus = new ReindexStatus(false, newIndex, indexed, failed, cursor,
                    System.currentTimeMillis() - started,
                    "Reindex aborted: " + e.getMessage() + ". Live alias unchanged; rerun is safe.");
        } finally {
            running.set(false);
        }

        return lastStatus;
    }

    private List<ComplaintDocument> loadBatch(long afterId, int batchSize) {
        return jdbcTemplate.query(COMPLAINT_PAGE_SQL,
                ps -> {
                    ps.setLong(1, afterId);
                    ps.setInt(2, batchSize);
                },
                rs -> {
                    List<ComplaintDocument> docs = new ArrayList<>();
                    while (rs.next()) {
                        docs.add(rowMapper.map(rs));
                    }
                    return docs;
                });
    }

    private void attachTimeline(List<ComplaintDocument> batch) {
        long low = Long.parseLong(batch.get(0).getComplaintId());
        long high = Long.parseLong(batch.get(batch.size() - 1).getComplaintId());

        Map<String, ComplaintDocument> byId = new LinkedHashMap<>();
        for (ComplaintDocument doc : batch) {
            byId.put(doc.getComplaintId(), doc);
        }

        jdbcTemplate.query(TIMELINE_SQL,
                ps -> {
                    ps.setLong(1, low);
                    ps.setLong(2, high);
                },
                rs -> {
                    ComplaintDocument doc = byId.get(String.valueOf(rs.getLong("complaint_id")));
                    if (doc != null) {
                        doc.getTimeline().add(rowMapper.mapTimeline(rs));
                    }
                });
    }

    private void attachAppealOrders(List<ComplaintDocument> batch) {
        Map<String, ComplaintDocument> byNumber = new LinkedHashMap<>();
        for (ComplaintDocument doc : batch) {
            if (doc.getComplaintNumber() != null) {
                byNumber.put(doc.getComplaintNumber(), doc);
            }
        }
        if (byNumber.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(byNumber.size(), "?"));
        String sql = APPEAL_ORDER_SQL.formatted(placeholders);
        Object[] args = byNumber.keySet().toArray();

        jdbcTemplate.query(sql, args, rs -> {
            ComplaintDocument doc = byNumber.get(rs.getString("complaint_number"));
            if (doc != null) {
                doc.getAppealOrders().add(rowMapper.mapAppealOrder(rs));
            }
        });
    }

    private BatchResult bulkWithRetry(String index, List<ComplaintDocument> batch) throws Exception {
        int attempts = properties.getReindex().getMaxRetriesPerBatch();
        Exception last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                BulkRequest.Builder request = new BulkRequest.Builder();
                for (ComplaintDocument doc : batch) {
                    request.operations(op -> op.index(idx -> idx
                            .index(index)
                            .id(doc.getComplaintId())
                            .document(doc)));
                }

                BulkResponse response = bulkClient.bulk(request.build());

                long failedItems = 0;
                if (response.errors()) {
                    for (BulkResponseItem item : response.items()) {
                        if (item.error() != null) {
                            failedItems++;
                            log.warn("Bulk item {} failed: {}", item.id(), item.error().reason());
                        }
                    }
                }
                return new BatchResult(batch.size() - failedItems, failedItems);

            } catch (Exception e) {
                last = e;
                log.warn("Bulk attempt {}/{} failed: {}", attempt, attempts, e.getMessage());
                if (attempt < attempts) {
                    Thread.sleep(Math.min(5000L, 250L * (1L << (attempt - 1))));
                }
            }
        }
        throw last;
    }

    private void pause() throws InterruptedException {
        long pause = properties.getReindex().getPauseBetweenBatchesMs();
        if (pause > 0) {
            Thread.sleep(pause);
        }
    }

    private record BatchResult(long succeeded, long failed) {
    }

    public record ReindexStatus(boolean success, String index, long indexed, long failed,
                                long lastCursor, long durationMs, String message) {

        static ReindexStatus idle() {
            return new ReindexStatus(true, null, 0, 0, 0, 0, "No reindex has run in this process");
        }

        ReindexStatus withMessage(String message) {
            return new ReindexStatus(success, index, indexed, failed, lastCursor, durationMs, message);
        }
    }
}
