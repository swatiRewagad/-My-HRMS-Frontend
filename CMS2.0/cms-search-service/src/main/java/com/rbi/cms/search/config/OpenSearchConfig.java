package com.rbi.cms.search.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cms.opensearch.enabled", havingValue = "true", matchIfMissing = true)
public class OpenSearchConfig {

    @Value("${cms.opensearch.host:localhost}")
    private String host;

    @Value("${cms.opensearch.port:9200}")
    private int port;

    @Value("${cms.opensearch.scheme:http}")
    private String scheme;

    @Value("${cms.opensearch.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${cms.opensearch.socket-timeout-ms:30000}")
    private int socketTimeoutMs;

    @Value("${cms.opensearch.max-connections:50}")
    private int maxConnections;

    @Value("${cms.opensearch.max-connections-per-route:20}")
    private int maxConnectionsPerRoute;

    /**
     * The client ships with no timeouts, which means a wedged cluster holds the calling thread
     * indefinitely — on the request path that is a Tomcat thread, so a single unresponsive node can
     * consume the whole pool and take the service down with it rather than failing one search.
     *
     * <p>The connection pool is bounded for the same reason: it caps how much of the cluster's
     * capacity a reindex can occupy while interactive searches are competing for it.
     */
    @Bean
    public OpenSearchClient openSearchClient() {
        HttpHost httpHost = new HttpHost(scheme, host, port);

        var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                        .setSocketTimeout(Timeout.ofMilliseconds(socketTimeoutMs))
                        .build())
                .build();

        var transport = ApacheHttpClient5TransportBuilder.builder(httpHost)
                .setHttpClientConfigCallback(httpClient -> httpClient
                        .setConnectionManager(connectionManager)
                        .setDefaultRequestConfig(RequestConfig.custom()
                                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                                .setResponseTimeout(Timeout.ofMilliseconds(socketTimeoutMs))
                                .build()))
                .build();

        return new OpenSearchClient(transport);
    }
}
