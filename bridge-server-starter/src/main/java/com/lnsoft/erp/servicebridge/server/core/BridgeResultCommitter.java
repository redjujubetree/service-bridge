package com.lnsoft.erp.servicebridge.server.core;

import com.lnsoft.erp.servicebridge.protocol.BridgeProcessStatus;
import com.lnsoft.erp.servicebridge.protocol.BridgeRequestMessage;
import com.lnsoft.erp.servicebridge.protocol.BridgeResponseMessage;
import com.lnsoft.erp.servicebridge.protocol.BridgeTopicProperties;
import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.store.BridgeResultStore;
import org.springframework.transaction.annotation.Transactional;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class BridgeResultCommitter {

    private final BridgeResultStore resultStore;
    private final MessageProducer messageProducer;
    private final BridgeTopicProperties topics;
    private final ServiceBridgeServerProperties properties;

    public BridgeResultCommitter(BridgeResultStore resultStore,
                                 MessageProducer messageProducer,
                                 BridgeTopicProperties topics,
                                 ServiceBridgeServerProperties properties) {
        this.resultStore = resultStore;
        this.messageProducer = messageProducer;
        this.topics = topics;
        this.properties = properties;
    }

    @Transactional
    public boolean completeSuccess(BridgeRequestMessage request,
                                   String attemptToken,
                                   String resultPayload) {
        LocalDateTime completedAt = LocalDateTime.now();
        boolean updated = resultStore.completeSuccess(
                request.getRequestId(), attemptToken, resultPayload, completedAt);
        if (!updated) {
            return false;
        }
        if (resultStore.isClientWaiting(request.getRequestId(), LocalDateTime.now())) {
            publish(request.getRequestId(), request.getBusinessType(),
                    BridgeProcessStatus.SUCCESS, null, completedAt);
        }
        return true;
    }

    @Transactional
    public boolean completeFailure(BridgeRequestMessage request,
                                   String attemptToken,
                                   String errorCode,
                                   String errorMessage) {
        return completeFailure(
                request.getRequestId(), request.getBusinessType(), attemptToken, errorCode, errorMessage);
    }

    @Transactional
    public boolean completeFailure(String requestId,
                                   String businessType,
                                   String attemptToken,
                                   String errorCode,
                                   String errorMessage) {
        LocalDateTime completedAt = LocalDateTime.now();
        boolean updated = resultStore.completeFailure(
                requestId, attemptToken, errorCode, errorMessage, completedAt);
        if (!updated) {
            return false;
        }
        if (resultStore.isClientWaiting(requestId, LocalDateTime.now())) {
            publish(requestId, businessType, BridgeProcessStatus.FAILED, errorCode, completedAt);
        }
        return true;
    }

    @Transactional
    public boolean completeExpiredFailure(String requestId,
                                          String businessType,
                                          String attemptToken) {
        LocalDateTime completedAt = LocalDateTime.now();
        String errorCode = "PROCESSING_TIMEOUT";
        boolean updated = resultStore.completeExpiredFailure(
                requestId, attemptToken, errorCode,
                "Server processing deadline exceeded", completedAt);
        if (!updated) {
            return false;
        }
        if (resultStore.isClientWaiting(requestId, LocalDateTime.now())) {
            publish(requestId, businessType, BridgeProcessStatus.FAILED, errorCode, completedAt);
        }
        return true;
    }

    private void publish(String requestId,
                         String businessType,
                         BridgeProcessStatus status,
                         String errorCode,
                         LocalDateTime completedAt) {
        BridgeResponseMessage response = new BridgeResponseMessage();
        response.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        response.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        response.setRequestId(requestId);
        response.setBusinessType(businessType);
        response.setProcessStatus(status);
        response.setErrorCode(errorCode);
        response.setCompletedAt(
                completedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        messageProducer.send(
                topics.getResponseTopic(),
                properties.getMessageSource(),
                response);
    }
}
