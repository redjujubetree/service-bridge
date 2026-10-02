package top.redjujubetree.servicebridge.client.model;

import com.fasterxml.jackson.databind.JsonNode;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.status.BridgeWaitStatus;

import java.time.LocalDateTime;

public class BridgeRequestRecord {

    private String requestId;
    private String protocolVersion;
    private String businessType;
    private JsonNode requestPayload;
    private BridgeWaitStatus waitStatus;
    private BridgeProcessStatus processStatus;
    private LocalDateTime clientDeadlineAt;
    private LocalDateTime createdAt;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public JsonNode getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(JsonNode requestPayload) {
        this.requestPayload = requestPayload;
    }

    public BridgeWaitStatus getWaitStatus() {
        return waitStatus;
    }

    public void setWaitStatus(BridgeWaitStatus waitStatus) {
        this.waitStatus = waitStatus;
    }

    public BridgeProcessStatus getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(BridgeProcessStatus processStatus) {
        this.processStatus = processStatus;
    }

    public LocalDateTime getClientDeadlineAt() {
        return clientDeadlineAt;
    }

    public void setClientDeadlineAt(LocalDateTime clientDeadlineAt) {
        this.clientDeadlineAt = clientDeadlineAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
