package com.rbi.cms.infra.kafka;

import com.rbi.cms.common.config.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 6;
    private static final int REPLICAS = 3;

    @Bean
    public NewTopic complaintIngestedTopic() {
        return TopicBuilder.name(KafkaTopics.COMPLAINT_INGESTED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic complaintAssignedTopic() {
        return TopicBuilder.name(KafkaTopics.COMPLAINT_ASSIGNED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic complaintInProgressTopic() {
        return TopicBuilder.name(KafkaTopics.COMPLAINT_IN_PROGRESS)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic complaintEscalatedTopic() {
        return TopicBuilder.name(KafkaTopics.COMPLAINT_ESCALATED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic complaintResolvedTopic() {
        return TopicBuilder.name(KafkaTopics.COMPLAINT_RESOLVED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic complaintClosedTopic() {
        return TopicBuilder.name(KafkaTopics.COMPLAINT_CLOSED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic complaintDlqTopic() {
        return TopicBuilder.name(KafkaTopics.COMPLAINT_DLQ)
                .partitions(3)
                .replicas(REPLICAS)
                .build();
    }
}
