package com.lnsoft.erp.servicebridge.server.core;

import com.lnsoft.erp.servicebridge.protocol.BridgeProcessStatus;
import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.model.BridgeProcessingRecord;
import com.lnsoft.erp.servicebridge.server.model.BridgeRecoveryMessage;
import com.lnsoft.erp.servicebridge.server.store.BridgeResultStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BridgeProcessingRecoveryTest {

    private BridgeResultStore store;
    private BridgeResultCommitter committer;
    private MessageProducer producer;
    private BridgeProcessingRecovery recovery;

    @BeforeEach
    void setUp() {
        store = mock(BridgeResultStore.class);
        committer = mock(BridgeResultCommitter.class);
        producer = mock(MessageProducer.class);
        recovery = new BridgeProcessingRecovery(store, committer, producer,
                new ServiceBridgeServerProperties());
    }

    @Test
    void staleRecoveryMessageCannotEndNewAttempt() {
        when(store.find("request-1")).thenReturn(Optional.of(record("token-b",
                LocalDateTime.now().minusMinutes(1))));

        recovery.recover(new BridgeRecoveryMessage("request-1", "token-a"));

        verifyNoInteractions(committer, producer);
    }

    @Test
    void earlyDeliveryIsRescheduled() {
        when(store.find("request-1")).thenReturn(Optional.of(record("token-a",
                LocalDateTime.now().plusMinutes(1))));
        BridgeRecoveryMessage message = new BridgeRecoveryMessage("request-1", "token-a");

        recovery.recover(message);

        verify(producer).sendDelayed(eq(ServiceBridgeProtocol.RECOVERY_TOPIC),
                eq("service-bridge-server"), eq(message), any(Duration.class));
        verifyNoInteractions(committer);
    }

    @Test
    void expiredAttemptIsCompletedThroughConditionalCommit() {
        when(store.find("request-1")).thenReturn(Optional.of(record("token-a",
                LocalDateTime.now().minusMinutes(1))));

        recovery.recover(new BridgeRecoveryMessage("request-1", "token-a"));

        verify(committer).completeExpiredFailure("request-1", "TEST", "token-a");
        verifyNoInteractions(producer);
    }

    private BridgeProcessingRecord record(String token, LocalDateTime expiresAt) {
        BridgeProcessingRecord record = new BridgeProcessingRecord();
        record.setRequestId("request-1");
        record.setBusinessType("TEST");
        record.setProcessStatus(BridgeProcessStatus.PROCESSING);
        record.setProcessingToken(token);
        record.setProcessingExpiresAt(expiresAt);
        return record;
    }
}
