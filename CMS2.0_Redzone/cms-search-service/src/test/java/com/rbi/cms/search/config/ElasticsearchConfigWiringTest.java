package com.rbi.cms.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bean wiring of {@link ElasticsearchConfig} against the ambiguity that made
 * cms-search-service unbootable.
 *
 * <p>The config publishes two beans of each Elasticsearch type — a short-timeout pair for
 * user-facing search and a long-timeout pair for bulk reindex. Three production injection points
 * take the type without qualifying it ({@code ComplaintIndexManager}, {@code ComplaintSearchService}
 * for the typed client, and the primary client itself for the {@code RestClient}). Two candidates
 * against an unqualified injection point is a startup failure, not a warning:
 * {@code NoUniqueBeanDefinitionException} at refresh, so the service never serves a request.
 *
 * <p>A compile is no evidence here at all — the ambiguity is invisible to javac and only appears
 * when the context refreshes. These tests therefore refresh a real context.
 *
 * <p>No Elasticsearch node is needed: {@code RestClient.builder(...).build()} resolves no host and
 * opens no socket, so the wiring is observable without the cluster being up.
 */
class ElasticsearchConfigWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(PropertiesHolder.class, ElasticsearchConfig.class);

    @Test
    @DisplayName("context refreshes: the ambiguity that blocked boot is gone")
    void contextRefreshes() {
        runner.run(context -> assertThat(context)
                .as("ElasticsearchConfig must not fail refresh on ambiguous beans")
                .hasNotFailed());
    }

    @Test
    @DisplayName("an unqualified ElasticsearchClient consumer resolves to the search client")
    void unqualifiedTypedClientResolves() {
        runner.withUserConfiguration(UnqualifiedConsumer.class).run(context -> {
            assertThat(context).hasNotFailed();
            // This is the shape of ComplaintIndexManager and ComplaintSearchService: the bare type,
            // injected by Lombok's generated constructor with no @Qualifier.
            assertThat(context.getBean(UnqualifiedConsumer.class).client)
                    .isSameAs(context.getBean("elasticsearchClient", ElasticsearchClient.class));
        });
    }

    @Test
    @DisplayName("the bulk client stays separately addressable by name")
    void bulkClientRemainsDistinct() {
        runner.withUserConfiguration(BulkConsumer.class).run(context -> {
            assertThat(context).hasNotFailed();
            // ReindexJob asks for this one by name. If @Primary had been applied by renaming or
            // collapsing the beans, the bulk socket timeout would silently become the 2s search
            // timeout and every reindex batch would time out.
            assertThat(context.getBean(BulkConsumer.class).client)
                    .isSameAs(context.getBean("bulkElasticsearchClient", ElasticsearchClient.class))
                    .isNotSameAs(context.getBean("elasticsearchClient", ElasticsearchClient.class));
        });
    }

    @Test
    @DisplayName("both timeout-distinguished pairs are still published")
    void bothPairsExist() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(ElasticsearchClient.class)).hasSize(2);
            assertThat(context.getBeansOfType(RestClient.class))
                    .containsOnlyKeys("elasticsearchRestClient", "bulkElasticsearchRestClient");
        });
    }

    @Test
    @DisplayName("the kill switch removes the clients entirely")
    void killSwitchRemovesClients() {
        // SearchProperties documents cms.search.enabled as an operator kill switch that creates no
        // client bean at all. @ConditionalOnProperty is what implements that, so it is asserted here
        // rather than assumed.
        runner.withPropertyValues("cms.search.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ElasticsearchClient.class)).isEmpty();
        });
    }

    @Configuration
    @EnableConfigurationProperties(SearchProperties.class)
    static class PropertiesHolder {
    }

    @Component
    @RequiredArgsConstructor
    static class UnqualifiedConsumer {
        private final ElasticsearchClient client;
    }

    @Component
    static class BulkConsumer {
        private final ElasticsearchClient client;

        BulkConsumer(@Qualifier("bulkElasticsearchClient") ElasticsearchClient client) {
            this.client = client;
        }
    }
}
