package com.lnsoft.erp.servicebridge.server.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.erp.servicebridge.protocol.BridgeMessageValidator;
import com.lnsoft.erp.servicebridge.protocol.BridgeRequestMessage;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.model.AcquireResult;

import java.time.LocalDateTime;
import java.util.UUID;

public class BridgeRequestExecutor {

    private final BridgeRequestHandlerRegistry handlerRegistry;
    private final BridgeRequestAcquirer requestAcquirer;
    private final BridgeResultCommitter resultCommitter;
    private final ServiceBridgeServerProperties properties;
    private final ObjectMapper objectMapper;

    public BridgeRequestExecutor(BridgeRequestHandlerRegistry handlerRegistry,
                                 BridgeRequestAcquirer requestAcquirer,
                                 BridgeResultCommitter resultCommitter,
                                 ServiceBridgeServerProperties properties,
                                 ObjectMapper objectMapper) {
        this.handlerRegistry = handlerRegistry;
        this.requestAcquirer = requestAcquirer;
        this.resultCommitter = resultCommitter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void execute(BridgeRequestMessage request) {
        BridgeMessageValidator.validate(request);
        validateTimeout();

        String attemptToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = LocalDateTime.now();
        AcquireResult acquired = requestAcquirer.acquire(
                request.getRequestId(), attemptToken, startedAt);

        if (acquired == AcquireResult.ALREADY_SUCCESS || acquired == AcquireResult.ALREADY_FAILED) {
            return;
        }
        if (acquired == AcquireResult.REQUEST_NOT_FOUND) {
            throw new BridgeRetryableException(
                    "ServiceBridge request row not found: " + request.getRequestId());
        }

        BridgeHandlerResult result;
        try {
            BridgeRequestHandler handler = handlerRegistry.findRequired(request.getBusinessType());
            result = handler.handle(toContext(request));
            if (result == null) {
                result = BridgeHandlerResult.failure(
                        "EMPTY_HANDLER_RESULT", "BridgeRequestHandler returned null");
            }
        } catch (BridgeRetryableException e) {
            throw e;
        } catch (UnsupportedBusinessTypeException e) {
            result = BridgeHandlerResult.failure("UNSUPPORTED_BUSINESS_TYPE", e.getMessage());
        } catch (Exception e) {
            result = BridgeHandlerResult.failure("HANDLER_ERROR", safeMessage(e));
        }

        if (result.isSuccess()) {
            resultCommitter.completeSuccess(request, attemptToken, serialize(result.getData()));
        } else {
            resultCommitter.completeFailure(
                    request,
                    attemptToken,
                    defaultCode(result.getErrorCode()),
                    result.getErrorMessage());
        }
    }

    private BridgeRequestContext toContext(BridgeRequestMessage request) {
        BridgeRequestContext context = new BridgeRequestContext();
        context.setRequestId(request.getRequestId());
        context.setBusinessType(request.getBusinessType());
        context.setPayload(request.getPayload());
        context.setCreatedAt(request.getCreatedAt());
        context.setClientDeadlineAt(request.getClientDeadlineAt());
        context.setTraceId(request.getTraceId());
        return context;
    }

    private String serialize(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize handler result", e);
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String defaultCode(String code) {
        return code == null || code.trim().isEmpty() ? "FAILED" : code;
    }

    private void validateTimeout() {
        if (properties.getProcessingTimeout() == null
                || properties.getProcessingTimeout().isZero()
                || properties.getProcessingTimeout().isNegative()) {
            throw new IllegalStateException(
                    "service.bridge.server.processing-timeout must be greater than zero");
        }
    }
}
