package com.lnsoft.glxx.servicebridge.server.model;

import com.lnsoft.glxx.servicebridge.protocol.BridgeProcessStatus;

import java.time.LocalDateTime;

public class BridgeProcessingRecord {

    private String requestId;
    private String businessType;
    private BridgeProcessStatus processStatus;
    private String processingToken;
    private LocalDateTime processingExpiresAt;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public BridgeProcessStatus getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(BridgeProcessStatus processStatus) {
        this.processStatus = processStatus;
    }

    public String getProcessingToken() {
        return processingToken;
    }

    public void setProcessingToken(String processingToken) {
        this.processingToken = processingToken;
    }

    public LocalDateTime getProcessingExpiresAt() {
        return processingExpiresAt;
    }

    public void setProcessingExpiresAt(LocalDateTime processingExpiresAt) {
        this.processingExpiresAt = processingExpiresAt;
    }
}
