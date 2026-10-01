package com.rbi.cms.search.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Decides whether the Elasticsearch-backed tests can run, and builds the client if so.
 *
 * <p>The language tests must exercise a real Elasticsearch: the whole question they answer is what
 * Lucene's analyzers actually do to Indic text, and a mock would only assert what the author already
 * believed. But a developer without a local node must not see a red build for it, so absence skips
 * and only a reachable-but-broken node fails.
 */
public final class ElasticsearchAvailable {

    private static final String HOST = System.getProperty("es.host",
            System.getenv().getOrDefault("ELASTICSEARCH_HOST", "localhost"));
    private static final int PORT = Integer.parseInt(System.getProperty("es.port",
            System.getenv().getOrDefault("ELASTICSEARCH_PORT", "9200")));

    private ElasticsearchAvailable() {
    }

    public static boolean isReachable() {
        try (RestClient client = restClient()) {
            return client.performRequest(new org.elasticsearch.client.Request("GET", "/"))
                    .getStatusLine().getStatusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The connect timeout stays short so an absent node skips fast, but the socket timeout must not.
     * Creating the complaint index compiles two custom analyzers plus five per-language subfields on
     * every text field, and on a cold single-node dev cluster that _create call alone measured well
     * over five seconds — which surfaced as a bogus SocketTimeoutException in setUp rather than as a
     * test result.
     */
    public static RestClient restClient() {
        return RestClient.builder(new HttpHost(HOST, PORT, "http"))
                .setRequestConfigCallback(rc -> rc.setConnectTimeout(1000).setSocketTimeout(60000))
                .build();
    }

    public static ElasticsearchClient client(RestClient restClient) {
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public static String describe() {
        return HOST + ":" + PORT;
    }
}
