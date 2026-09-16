package com.lnsoft.erp.servicebridge.server.core;

public class BridgeHandlerResult {

    private boolean success;
    private Object data;
    private String errorCode;
    private String errorMessage;

    public static BridgeHandlerResult success(Object data) {
        BridgeHandlerResult result = new BridgeHandlerResult();
        result.success = true;
        result.data = data;
        return result;
    }

    public static BridgeHandlerResult failure(String errorCode, String errorMessage) {
        BridgeHandlerResult result = new BridgeHandlerResult();
        result.success = false;
        result.errorCode = errorCode;
        result.errorMessage = errorMessage;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
