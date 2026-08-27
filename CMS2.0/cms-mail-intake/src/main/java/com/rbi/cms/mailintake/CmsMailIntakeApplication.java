package com.rbi.cms.mailintake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.rbi.cms.mailintake", "com.rbi.cms.common"})
@ConfigurationPropertiesScan(basePackages = "com.rbi.cms.mailintake.config")
@EnableScheduling // drives ParserScheduler — the async parser worker pool, see its Javadoc
public class CmsMailIntakeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsMailIntakeApplication.class, args);
    }
}
