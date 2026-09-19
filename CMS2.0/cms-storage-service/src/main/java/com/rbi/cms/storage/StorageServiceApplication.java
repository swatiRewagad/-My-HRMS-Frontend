package com.rbi.cms.storage;

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
public class StorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }
}
