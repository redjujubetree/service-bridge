package com.lnsoft.glxx.servicebridge.server.core;

public interface BridgeRequestHandler {

    String businessType();

    BridgeHandlerResult handle(BridgeRequestContext context) throws Exception;
}
