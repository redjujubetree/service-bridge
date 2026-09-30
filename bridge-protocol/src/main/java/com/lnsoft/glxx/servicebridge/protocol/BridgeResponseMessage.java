package com.lnsoft.glxx.servicebridge.protocol;

public class BridgeResponseMessage {

    private String protocolVersion;
    private String messageId;
    private String requestId;
    private String businessType;
    private BridgeProcessStatus processStatus;
    private long completedAt;
    private String errorCode;

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

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

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
