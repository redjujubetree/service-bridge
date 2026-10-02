package top.redjujubetree.servicebridge.client.model;

import top.redjujubetree.servicebridge.protocol.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.BridgeWaitStatus;

public class BridgeStoredResult {

    private String requestId;
    private String businessType;
    private BridgeWaitStatus waitStatus;
    private BridgeProcessStatus processStatus;
    private String resultPayload;
    private String errorCode;
    private String errorMessage;

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

    public String getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(String resultPayload) {
        this.resultPayload = resultPayload;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
