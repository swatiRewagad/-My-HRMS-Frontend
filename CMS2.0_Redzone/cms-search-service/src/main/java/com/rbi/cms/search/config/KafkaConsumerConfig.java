package com.rbi.cms.search.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@ConditionalOnProperty(name = "cms.search.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {

    /**
     * Bounded retry. The default container behaviour retries a failing record indefinitely, which is
     * how one poison message wedges a consumer group: the partition never advances and every
     * complaint behind it stops being indexed.
     *
     * Four attempts over roughly 3.5s covers what this consumer actually fails on — a brief
     * Elasticsearch blip — and then gives up so the listener's own dead-letter path can run and the
     * partition can move on.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        ExponentialBackOff backOff = new ExponentialBackOff(250L, 2.0);
        backOff.setMaxAttempts(4);
        backOff.setMaxInterval(2000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
