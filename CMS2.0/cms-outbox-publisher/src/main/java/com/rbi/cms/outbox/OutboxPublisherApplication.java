package com.rbi.cms.outbox;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
public class OutboxPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboxPublisherApplication.class, args);
    }
}
