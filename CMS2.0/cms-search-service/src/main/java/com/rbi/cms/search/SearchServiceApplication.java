package com.rbi.cms.search;

import com.rbi.cms.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The shared advice is imported by type rather than component-scanned: widening the scan to
 * {@code com.rbi.cms} would also instantiate everything under {@code cms-common}'s {@code config}
 * and {@code crypto} packages, which this service does not use and which can fail startup.
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
