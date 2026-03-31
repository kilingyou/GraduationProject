package com.scm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Scheduler settings for PDF "data sync / automation" requirements.
 */
@Data
@Component
@ConfigurationProperties(prefix = "scm.scheduler")
public class SchedulerProperties {

    private boolean enabled = true;

    private long recallScanIntervalMs = 60_000L;

    private int recallThresholdCount = 3;

    private int recallThresholdWindowMinutes = 60;

    /**
     * DRAFT / PUBLISHED
     */
    private String recallNoticeDefaultStatus = "DRAFT";
}

