package com.hrms.cms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "cms.duplicate-check")
@Getter @Setter
public class DuplicateCheckProperties {

    private int lookbackDays = 90;
    private List<String> terminalStatuses = List.of("closed", "rejected", "withdrawn");
}
