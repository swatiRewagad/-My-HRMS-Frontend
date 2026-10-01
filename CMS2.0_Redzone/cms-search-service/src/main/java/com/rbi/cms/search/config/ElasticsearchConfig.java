package com.rbi.cms.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.search.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchConfig {

    private final SearchProperties properties;

    /**
     * The low-level client carries the timeouts, because they are HTTP-level concerns: the typed
     * {@link ElasticsearchClient} has no timeout of its own and will wait for the socket forever if
     * the socket is allowed to.
     */
    @Bean(destroyMethod = "close")
    @Primary
    public RestClient elasticsearchRestClient() {
        return buildRestClient(properties.getSocketTimeoutMs());
    }

    /**
     * The default client, and deliberately the {@link Primary} one of the two.
     *
     * <p>Both this and {@code bulkElasticsearchClient} are {@code ElasticsearchClient}s, so an
     * injection point that does not qualify has to be resolved by something. Making the
     * short-timeout search client primary means a consumer that forgets to qualify inherits the 2s
     * read timeout that protects user-facing search, rather than the multi-second bulk one — the
     * failure mode of the wrong default is then a timed-out query, not an exhausted request pool.
     *
     * <p>{@code ReindexJob} is the only caller that wants the other client and asks for it by name.
     */
    @Bean
    @Primary
    public ElasticsearchClient elasticsearchClient(
            @Qualifier("elasticsearchRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));
    }

    /**
     * A second client, separated only by its socket timeout.
     *
     * Reindex bulk calls legitimately take seconds, so they cannot live under the 2s read timeout that
     * protects user-facing search. Sharing one client would force a choice between a reindex that
     * always times out and a search timeout long enough to exhaust the request pool.
     */
    @Bean(name = "bulkElasticsearchRestClient", destroyMethod = "close")
    public RestClient bulkElasticsearchRestClient() {
        return buildRestClient(properties.getBulkSocketTimeoutMs());
    }

    @Bean(name = "bulkElasticsearchClient")
    public ElasticsearchClient bulkElasticsearchClient(
            @Qualifier("bulkElasticsearchRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));
    }

    private RestClient buildRestClient(int socketTimeoutMs) {
        HttpHost httpHost = new HttpHost(properties.getHost(), properties.getPort(), properties.getScheme());

        RestClientBuilder builder = RestClient.builder(httpHost)
                .setRequestConfigCallback(rc -> rc
                        .setConnectTimeout(properties.getConnectTimeoutMs())
                        .setSocketTimeout(socketTimeoutMs)
                        .setConnectionRequestTimeout(properties.getConnectTimeoutMs()));

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(credentials));
        }

        log.info("Elasticsearch client -> {}://{}:{} (connect={}ms, socket={}ms)",
                properties.getScheme(), properties.getHost(), properties.getPort(),
                properties.getConnectTimeoutMs(), socketTimeoutMs);

        return builder.build();
    }
}
