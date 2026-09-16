package com.lnsoft.erp.servicebridge.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "service.bridge.server")
public class ServiceBridgeServerProperties {

    private boolean enabled = true;
    private String messageSource = "service-bridge-server";
    private Duration processingTimeout = Duration.ofMinutes(15);
    private boolean recoveryEnabled = true;
    private Duration recoveryInterval = Duration.ofMinutes(1);
    private int recoveryBatchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMessageSource() {
        return messageSource;
    }

    public void setMessageSource(String messageSource) {
        this.messageSource = messageSource;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public boolean isRecoveryEnabled() {
        return recoveryEnabled;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public Duration getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(Duration recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = recoveryBatchSize;
    }
}
