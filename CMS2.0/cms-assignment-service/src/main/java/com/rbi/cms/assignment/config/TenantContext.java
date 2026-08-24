package com.rbi.cms.assignment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    @Value("${cms.assignment.default-tenant:RBI-CMS}")
    private String defaultTenant;

    public String getCurrentTenant() {
        return defaultTenant;
    }
}
