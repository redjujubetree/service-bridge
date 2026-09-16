package com.lnsoft.erp.servicebridge.protocol;

public enum BridgeProcessStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}
