package com.lnsoft.erp.servicebridge.client.core;

import java.util.UUID;

public class UuidBridgeRequestIdGenerator implements BridgeRequestIdGenerator {

    @Override
    public String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
