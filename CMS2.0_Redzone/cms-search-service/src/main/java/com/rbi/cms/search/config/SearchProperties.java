package com.rbi.cms.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cms.search")
public class SearchProperties {

    /**
     * Kill switch. When false no Elasticsearch client bean is created at all, the listeners do not
     * register, and the controller answers 503 "search unavailable".
     *
     * This exists so search can be taken out of the request path by a ConfigMap edit and a pod
     * restart rather than a deploy. Production HikariCP is 100 connections at a 3000ms timeout; an
     * Elasticsearch node that accepts a socket and then never answers pins request threads, and the
     * operator needs a way to stop that inside minutes.
     */
    private boolean enabled = true;

    private String host = "localhost";
    private int port = 9200;
    private String scheme = "http";
    private String username;
    private String password;

    /**
     * The alias every read and every Kafka write goes through. Never an index name: reindex builds a
     * new concrete index and repoints this alias atomically, so readers are never pointed at a
     * half-populated index.
     */
    private String alias = "cms-complaints";

    /** Connect timeout, ms. Cheap to fail: either the node is listening or it is not. */
    private int connectTimeoutMs = 1000;

    /**
     * Socket timeout, ms. This is the number that decides whether a slow Elasticsearch becomes a
     * request-thread outage. A user-facing search that cannot answer in 2s is already a failed
     * search, so waiting longer buys nothing and costs threads.
     */
    private int socketTimeoutMs = 2000;

    /** Separate, longer socket timeout for bulk reindex calls, which legitimately take seconds. */
    private int bulkSocketTimeoutMs = 30000;

    private final Reindex reindex = new Reindex();

    @Getter
    @Setter
    public static class Reindex {
        private int batchSize = 500;

        /**
         * Pause between batches. The point is not throughput, it is that a reindex must be safe to
         * run against production during business hours: this yields the DB and the ES write queue
         * back to live traffic between batches.
         */
        private long pauseBetweenBatchesMs = 200;

        private int maxRetriesPerBatch = 3;

        /** Cap on how many complaints one invocation will walk. 0 means no cap. */
        private long maxDocuments = 0;
    }
}
