package com.rbi.cms.eligibility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rbi.cms.eligibility", "com.rbi.cms.common"})
public class EligibilityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EligibilityServiceApplication.class, args);
    }
}
