package com.lnsoft.glxx.servicebridge.server.core;

import com.lnsoft.glxx.servicebridge.protocol.BridgeRequestMessage;
import com.lnsoft.glxx.servicebridge.protocol.BridgeTopicProperties;
import com.lnsoft.glxx.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.glxx.servicebridge.server.store.BridgeResultStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeResultCommitterTest {

    private BridgeResultStore store;
    private MessageProducer producer;
    private BridgeResultCommitter committer;
    private BridgeTopicProperties topics;

    @BeforeEach
    void setUp() {
        store = mock(BridgeResultStore.class);
        producer = mock(MessageProducer.class);
        topics = new BridgeTopicProperties();
        topics.setResponseTopic("custom.response");
        committer = new BridgeResultCommitter(
                store, producer, topics, new ServiceBridgeServerProperties());
    }

    @Test
    void completedRequestNotifiesWaitingClient() {
        when(store.completeSuccess(eq("request-1"), eq("token"), eq("{}"), any(LocalDateTime.class)))
                .thenReturn(true);
        when(store.isClientWaiting(eq("request-1"), any(LocalDateTime.class))).thenReturn(true);

        committer.completeSuccess(request(), "token", "{}");

        verify(producer).send(eq("custom.response"),
                eq("service-bridge-server"), any());
    }

    @Test
    void completedRequestDoesNotNotifyTimedOutClient() {
        when(store.completeSuccess(eq("request-1"), eq("token"), eq("{}"), any(LocalDateTime.class)))
                .thenReturn(true);

        committer.completeSuccess(request(), "token", "{}");

        verify(producer, never()).send(eq("custom.response"), any(), any());
    }

    @Test
    void recoveryFailureDoesNotNotifyTimedOutClient() {
        when(store.completeExpiredFailure(eq("request-1"), eq("token"), eq("PROCESSING_TIMEOUT"),
                any(), any(LocalDateTime.class))).thenReturn(true);

        committer.completeExpiredFailure("request-1", "TEST", "token");

        verify(producer, never()).send(eq("custom.response"), any(), any());
    }

    @Test
    void recoveryFailureNotifiesWaitingClient() {
        when(store.completeExpiredFailure(eq("request-1"), eq("token"), eq("PROCESSING_TIMEOUT"),
                any(), any(LocalDateTime.class))).thenReturn(true);
        when(store.isClientWaiting(eq("request-1"), any(LocalDateTime.class))).thenReturn(true);

        committer.completeExpiredFailure("request-1", "TEST", "token");

        verify(producer).send(eq("custom.response"),
                eq("service-bridge-server"), any());
    }

    private BridgeRequestMessage request() {
        BridgeRequestMessage request = new BridgeRequestMessage();
        request.setRequestId("request-1");
        request.setBusinessType("TEST");
        return request;
    }
}
