package top.redjujubetree.servicebridge.server.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.protocol.message.BridgeRequestMessage;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;
import top.redjujubetree.servicebridge.server.config.ServiceBridgeServerProperties;
import top.redjujubetree.servicebridge.server.exception.BridgeRetryableException;
import top.redjujubetree.servicebridge.server.model.AcquireResult;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BridgeRequestExecutorRetryTest {
    @Test
    void retryableFailureReleasesAttemptBeforeRedelivery() throws Exception {
        BridgeRequestAcquirer acquirer = mock(BridgeRequestAcquirer.class);
        when(acquirer.acquire(eq("request-1"), any(), any())).thenReturn(AcquireResult.ACQUIRED);
        BridgeRequestHandler handler = new BridgeRequestHandler() {
            public String businessType() { return "TEST"; }
            public BridgeHandlerResult handle(BridgeRequestContext context) {
                throw new BridgeRetryableException("temporary failure");
            }
        };
        ObjectMapper mapper = new ObjectMapper();
        BridgeRequestMessage request = new BridgeRequestMessage();
        request.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        request.setMessageId("message-1");
        request.setRequestId("request-1");
        request.setBusinessType("TEST");
        request.setPayload(mapper.readTree("{}"));
        BridgeResultStore store = mock(BridgeResultStore.class);
        when(store.findRequest("request-1")).thenReturn(java.util.Optional.of(request));
        BridgeRequestExecutor executor = new BridgeRequestExecutor(
                new BridgeRequestHandlerRegistry(Collections.singletonList(handler)), acquirer,
                mock(BridgeResultCommitter.class), store, new BridgeTopicProperties(),
                new ServiceBridgeServerProperties(), mapper);

        assertThrows(BridgeRetryableException.class,
                () -> executor.execute("request-1", "service-bridge.request"));

        verify(acquirer).releaseForRetry(eq("request-1"), any());
    }

    @Test
    void rejectsRequestSentToWrongTopicAfterLoadingDatabaseRow() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BridgeRequestMessage request = new BridgeRequestMessage();
        request.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        request.setMessageId("request-1");
        request.setRequestId("request-1");
        request.setBusinessType("ORDER");
        request.setPayload(mapper.readTree("{}"));
        BridgeResultStore store = mock(BridgeResultStore.class);
        when(store.findRequest("request-1")).thenReturn(java.util.Optional.of(request));
        BridgeRequestAcquirer acquirer = mock(BridgeRequestAcquirer.class);
        BridgeTopicProperties topics = new BridgeTopicProperties();
        topics.getRequestTopics().put("ORDER", "orders.request");
        BridgeRequestExecutor executor = new BridgeRequestExecutor(
                new BridgeRequestHandlerRegistry(Collections.emptyList()), acquirer,
                mock(BridgeResultCommitter.class), store, topics,
                new ServiceBridgeServerProperties(), mapper);

        assertThrows(IllegalArgumentException.class,
                () -> executor.execute("request-1", "payments.request"));
        verifyNoInteractions(acquirer);
    }
}
