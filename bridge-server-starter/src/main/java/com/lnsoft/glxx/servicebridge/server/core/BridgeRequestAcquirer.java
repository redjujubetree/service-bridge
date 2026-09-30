package com.lnsoft.glxx.servicebridge.server.core;

import com.lnsoft.glxx.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.glxx.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.glxx.servicebridge.server.model.AcquireResult;
import com.lnsoft.glxx.servicebridge.server.model.BridgeRecoveryMessage;
import com.lnsoft.glxx.servicebridge.server.store.BridgeResultStore;
import org.springframework.transaction.annotation.Transactional;
import top.redjujubetree.bedrock.mq.entity.BedrockSubscription;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.time.LocalDateTime;
import java.util.List;

public class BridgeRequestAcquirer {

    private final BridgeResultStore resultStore;
    private final MessageProducer messageProducer;
    private final BedrockSubscriptionMapper subscriptionMapper;
    private final ServiceBridgeServerProperties properties;

    public BridgeRequestAcquirer(BridgeResultStore resultStore,
                                 MessageProducer messageProducer,
                                 BedrockSubscriptionMapper subscriptionMapper,
                                 ServiceBridgeServerProperties properties) {
        this.resultStore = resultStore;
        this.messageProducer = messageProducer;
        this.subscriptionMapper = subscriptionMapper;
        this.properties = properties;
    }

    @Transactional
    public AcquireResult acquire(String requestId, String attemptToken, LocalDateTime startedAt) {
        AcquireResult result = resultStore.tryAcquire(requestId, attemptToken, startedAt,
                startedAt.plusNanos(properties.getProcessingTimeout().toNanos()));
        if (result == AcquireResult.ACQUIRED && properties.isRecoveryEnabled()) {
            if (!hasRecoverySubscriber()) {
                throw new IllegalStateException("No active BedrockMQ recovery subscriber");
            }
            messageProducer.sendDelayed(ServiceBridgeProtocol.RECOVERY_TOPIC,
                    properties.getMessageSource(),
                    new BridgeRecoveryMessage(requestId, attemptToken),
                    properties.getProcessingTimeout());
        }
        return result;
    }

    private boolean hasRecoverySubscriber() {
        List<BedrockSubscription> subscriptions =
                subscriptionMapper.findEnabledByTopic(ServiceBridgeProtocol.RECOVERY_TOPIC);
        if (subscriptions == null) {
            return false;
        }
        for (BedrockSubscription subscription : subscriptions) {
            if (ServiceBridgeProtocol.RECOVERY_CONSUMER.equals(subscription.getConsumer())) {
                return true;
            }
        }
        return false;
    }
}
