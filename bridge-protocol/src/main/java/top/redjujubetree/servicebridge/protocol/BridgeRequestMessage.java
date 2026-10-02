package top.redjujubetree.servicebridge.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public class BridgeRequestMessage {

    private String protocolVersion;
    private String messageId;
    private String requestId;
    private String businessType;
    private JsonNode payload;
    private long createdAt;
    private long clientDeadlineAt;
    private String traceId;

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

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getClientDeadlineAt() {
        return clientDeadlineAt;
    }

    public void setClientDeadlineAt(long clientDeadlineAt) {
        this.clientDeadlineAt = clientDeadlineAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
