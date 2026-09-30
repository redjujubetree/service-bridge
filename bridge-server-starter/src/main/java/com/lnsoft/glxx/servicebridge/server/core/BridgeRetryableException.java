package com.lnsoft.glxx.servicebridge.server.core;

public class BridgeRetryableException extends RuntimeException {

    public BridgeRetryableException(String message) {
        super(message);
    }

    public BridgeRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
