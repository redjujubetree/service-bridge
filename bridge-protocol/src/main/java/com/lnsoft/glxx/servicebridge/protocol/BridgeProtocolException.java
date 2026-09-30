package com.lnsoft.glxx.servicebridge.protocol;

public class BridgeProtocolException extends RuntimeException {

    public BridgeProtocolException(String message) {
        super(message);
    }

    public BridgeProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
