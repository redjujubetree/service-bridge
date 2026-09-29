package com.lnsoft.erp.servicebridge.server.core;

import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.model.AcquireResult;
import com.lnsoft.erp.servicebridge.server.model.BridgeRecoveryMessage;
import com.lnsoft.erp.servicebridge.server.store.BridgeResultStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.redjujubetree.bedrock.mq.entity.BedrockSubscription;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BridgeRequestAcquirerTest {

    @Test
    void schedulesRecoveryOnlyForAcquiredAttempt() {
        BridgeResultStore store = mock(BridgeResultStore.class);
        MessageProducer producer = mock(MessageProducer.class);
        BedrockSubscriptionMapper subscriptions = mock(BedrockSubscriptionMapper.class);
        BedrockSubscription recoverySubscription = new BedrockSubscription();
        recoverySubscription.setConsumer(ServiceBridgeProtocol.RECOVERY_CONSUMER);
        when(subscriptions.findEnabledByTopic(ServiceBridgeProtocol.RECOVERY_TOPIC))
                .thenReturn(Collections.singletonList(recoverySubscription));
        ServiceBridgeServerProperties properties = new ServiceBridgeServerProperties();
        BridgeRequestAcquirer acquirer = new BridgeRequestAcquirer(
                store, producer, subscriptions, properties);
        LocalDateTime now = LocalDateTime.now();
        when(store.tryAcquire(eq("request-1"), eq("token-a"), eq(now), any(LocalDateTime.class)))
                .thenReturn(AcquireResult.ACQUIRED);
        when(store.tryAcquire(eq("request-1"), eq("token-b"), eq(now), any(LocalDateTime.class)))
                .thenReturn(AcquireResult.ALREADY_FAILED);

        acquirer.acquire("request-1", "token-a", now);
        acquirer.acquire("request-1", "token-b", now);

        ArgumentCaptor<BridgeRecoveryMessage> payload = ArgumentCaptor.forClass(BridgeRecoveryMessage.class);
        verify(producer).sendDelayed(eq(ServiceBridgeProtocol.RECOVERY_TOPIC),
                eq(properties.getMessageSource()), payload.capture(), eq(Duration.ofMinutes(15)));
        assertEquals("request-1", payload.getValue().getRequestId());
        assertEquals("token-a", payload.getValue().getAttemptToken());
        verifyNoMoreInteractions(producer);
    }

    @Test
    void missingRecoverySubscriberPreventsAcquisition() {
        BridgeResultStore store = mock(BridgeResultStore.class);
        MessageProducer producer = mock(MessageProducer.class);
        BedrockSubscriptionMapper subscriptions = mock(BedrockSubscriptionMapper.class);
        BridgeRequestAcquirer acquirer = new BridgeRequestAcquirer(
                store, producer, subscriptions, new ServiceBridgeServerProperties());
        when(store.tryAcquire(eq("request-1"), eq("token-a"), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(AcquireResult.ACQUIRED);

        assertThrows(IllegalStateException.class,
                () -> acquirer.acquire("request-1", "token-a", LocalDateTime.now()));

        verifyNoMoreInteractions(producer);
    }
}
