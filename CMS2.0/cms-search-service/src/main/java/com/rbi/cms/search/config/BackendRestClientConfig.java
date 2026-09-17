package com.rbi.cms.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The single HTTP client for talking to {@code cms-backend}.
 *
 * <p>Exists mainly to impose timeouts. The default {@code new RestTemplate()} has none at all, so a
 * backend that accepts a connection and then stalls holds the calling thread indefinitely — which for
 * the reindex walk means a Tomcat thread parked forever per page, and for the officer directory means a
 * hung refresh. A bounded read timeout turns both into a recoverable error.
 */
@Configuration
public class BackendRestClientConfig {

    @Bean
    public RestClient backendRestClient(@Value("${cms.backend.base-url:http://localhost:8082}") String baseUrl,
                                        @Value("${cms.backend.connect-timeout-ms:5000}") long connectTimeoutMs,
                                        @Value("${cms.backend.read-timeout-ms:30000}") long readTimeoutMs) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
