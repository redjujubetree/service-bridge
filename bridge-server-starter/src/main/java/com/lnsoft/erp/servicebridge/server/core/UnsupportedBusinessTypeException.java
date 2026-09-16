package com.lnsoft.erp.servicebridge.server.core;

public class UnsupportedBusinessTypeException extends RuntimeException {

    private final String businessType;

    public UnsupportedBusinessTypeException(String businessType) {
        super("Unsupported ServiceBridge businessType: " + businessType);
        this.businessType = businessType;
    }

    public String getBusinessType() {
        return businessType;
    }
}
