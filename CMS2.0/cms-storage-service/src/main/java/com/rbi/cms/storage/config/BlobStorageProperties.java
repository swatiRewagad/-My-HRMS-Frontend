package com.rbi.cms.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "cms.storage.blob")
@Getter
@Setter
public class BlobStorageProperties {

    private long maxFileSize = 52428800L; // 50MB
    private String allowedTypes = "pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip";

    public Set<String> allowedTypeSet() {
        return Arrays.stream(allowedTypes.split(","))
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
    }

    public boolean isAllowedType(String fileName) {
        if (fileName == null || !fileName.contains(".")) return false;
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return allowedTypeSet().contains(ext);
    }
}
