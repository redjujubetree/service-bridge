package com.lnsoft.glxx.servicebridge.server.core;

import com.lnsoft.glxx.servicebridge.protocol.BridgeProcessStatus;
import com.lnsoft.glxx.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.glxx.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.glxx.servicebridge.server.model.BridgeProcessingRecord;
import com.lnsoft.glxx.servicebridge.server.model.BridgeRecoveryMessage;
import com.lnsoft.glxx.servicebridge.server.store.BridgeResultStore;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class BridgeProcessingRecovery {

    private final BridgeResultStore resultStore;
    private final BridgeResultCommitter resultCommitter;
    private final MessageProducer messageProducer;
    private final ServiceBridgeServerProperties properties;

    public BridgeProcessingRecovery(BridgeResultStore resultStore,
                                    BridgeResultCommitter resultCommitter,
                                    MessageProducer messageProducer,
                                    ServiceBridgeServerProperties properties) {
        this.resultStore = resultStore;
        this.resultCommitter = resultCommitter;
        this.messageProducer = messageProducer;
        this.properties = properties;
    }

    public void recover(BridgeRecoveryMessage message) {
        if (message == null || message.getRequestId() == null
                || message.getRequestId().trim().isEmpty()
                || message.getAttemptToken() == null
                || message.getAttemptToken().trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid ServiceBridge recovery message");
        }
        Optional<BridgeProcessingRecord> stored = resultStore.find(message.getRequestId());
        if (!stored.isPresent() || stored.get().getProcessStatus() != BridgeProcessStatus.PROCESSING
                || !message.getAttemptToken().equals(stored.get().getProcessingToken())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = stored.get().getProcessingExpiresAt();
        if (expiresAt != null && now.isBefore(expiresAt)) {
            Duration remaining = Duration.between(now, expiresAt).plusMillis(1);
            messageProducer.sendDelayed(ServiceBridgeProtocol.RECOVERY_TOPIC,
                    properties.getMessageSource(), message, remaining);
            return;
        }
        resultCommitter.completeExpiredFailure(stored.get().getRequestId(),
                stored.get().getBusinessType(), message.getAttemptToken());
    }
}
