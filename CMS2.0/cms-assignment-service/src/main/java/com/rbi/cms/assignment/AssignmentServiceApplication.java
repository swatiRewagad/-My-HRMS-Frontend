package com.rbi.cms.assignment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssignmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssignmentServiceApplication.class, args);
    }
}
