package top.redjujubetree.servicebridge.server.core;

import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.message.BridgeRequestMessage;
import top.redjujubetree.servicebridge.protocol.message.BridgeResponseMessage;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class BridgeResultCommitter {

    private static final Logger log = LoggerFactory.getLogger(BridgeResultCommitter.class);

    private final BridgeResultStore resultStore;
    private final BridgeMessagePublisher publisher;
    private final BridgeTopicProperties topics;

    public BridgeResultCommitter(BridgeResultStore resultStore,
                                 BridgeMessagePublisher publisher,
                                 BridgeTopicProperties topics) {
        this.resultStore = resultStore;
        this.publisher = publisher;
        this.topics = topics;
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
        Runnable send = () -> {
            try {
                publisher.send(topics.getResponseTopic(), requestId, response);
            } catch (RuntimeException e) {
                log.warn("Failed to send ServiceBridge response notification for {}", requestId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
