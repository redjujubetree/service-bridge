package com.lnsoft.glxx.servicebridge.server.core;

import com.fasterxml.jackson.databind.JsonNode;

public class BridgeRequestContext {

    private String requestId;
    private String businessType;
    private JsonNode payload;
    private long createdAt;
    private long clientDeadlineAt;
    private String traceId;

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
