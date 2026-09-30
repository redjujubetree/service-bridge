package com.lnsoft.glxx.servicebridge.client.core;

public class TooManyWaitingRequestsException extends RuntimeException {

    public TooManyWaitingRequestsException(String message) {
        super(message);
    }
}
