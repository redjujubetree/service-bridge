package top.redjujubetree.servicebridge.server.core;

import top.redjujubetree.servicebridge.server.exception.UnsupportedBusinessTypeException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BridgeRequestHandlerRegistry {

    private final Map<String, BridgeRequestHandler> handlers;

    public BridgeRequestHandlerRegistry(List<BridgeRequestHandler> handlerList) {
        Map<String, BridgeRequestHandler> values = new LinkedHashMap<String, BridgeRequestHandler>();
        for (BridgeRequestHandler handler : handlerList) {
            String businessType = handler.businessType();
            if (businessType == null || businessType.trim().isEmpty()) {
                throw new IllegalStateException("BridgeRequestHandler.businessType must not be blank");
            }
            BridgeRequestHandler previous = values.put(businessType, handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ServiceBridge businessType: " + businessType);
            }
        }
        this.handlers = Collections.unmodifiableMap(values);
    }

    public BridgeRequestHandler findRequired(String businessType) {
        BridgeRequestHandler handler = handlers.get(businessType);
        if (handler == null) {
            throw new UnsupportedBusinessTypeException(businessType);
        }
        return handler;
    }

    public int size() {
        return handlers.size();
    }
}
