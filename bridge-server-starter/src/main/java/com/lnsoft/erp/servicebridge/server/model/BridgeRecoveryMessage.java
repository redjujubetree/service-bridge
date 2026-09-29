package com.lnsoft.erp.servicebridge.server.model;

public class BridgeRecoveryMessage {

    private String requestId;
    private String attemptToken;

    public BridgeRecoveryMessage() {
    }

    public BridgeRecoveryMessage(String requestId, String attemptToken) {
        this.requestId = requestId;
        this.attemptToken = attemptToken;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAttemptToken() {
        return attemptToken;
    }

    public void setAttemptToken(String attemptToken) {
        this.attemptToken = attemptToken;
    }
}
