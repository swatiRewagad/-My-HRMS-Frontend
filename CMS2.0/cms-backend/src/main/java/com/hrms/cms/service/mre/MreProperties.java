package com.hrms.cms.service.mre;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cms.mre")
public class MreProperties {

    private int version = 1;

    private int reWindowDays = 30;

    private int npciWindowDays = 30;

    private int cardNetworkWindowDays = 60;

    private int filingDeadlineDays = 90;

    private int limitationPeriodYears = 3;

    private WindowBasis windowBasis = WindowBasis.BUSINESS;

    public enum WindowBasis {
        CALENDAR,
        BUSINESS
    }
}
