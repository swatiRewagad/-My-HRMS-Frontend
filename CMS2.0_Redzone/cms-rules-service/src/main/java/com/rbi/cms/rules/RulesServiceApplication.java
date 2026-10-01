package com.rbi.cms.rules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rbi.cms.rules", "com.rbi.cms.common"})
public class RulesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RulesServiceApplication.class, args);
    }
}
