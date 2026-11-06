package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import top.redjujubetree.servicebridge.client.config.ServiceBridgeClientProperties;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.status.BridgeWaitStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomResponseHandlerTest {

    private final BridgeResponseHandler<String> handler = new BridgeResponseHandler<String>() {
        @Override
        public ResponseEntity<String> fromStored(BridgeStoredResult stored) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(stored.getResultPayload());
        }

        @Override
        public ResponseEntity<String> timeout(String requestId) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("timeout:" + requestId);
        }

        @Override
        public ResponseEntity<String> unavailable(String requestId, String message) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("unavailable:" + requestId);
        }
    };

    @Test
    void completionUsesPerRequestResponseHandler() {
        BridgeRequestStore store = mock(BridgeRequestStore.class);
        when(store.markRespondedIfWaiting(eq("request-1"), any())).thenReturn(true);
        TaskWaitRegistry registry = new TaskWaitRegistry(1);
        DeferredResult<ResponseEntity<String>> deferred = new DeferredResult<ResponseEntity<String>>();
        WaitContext<String> context = new WaitContext<String>("request-1", deferred, handler);
        registry.register(context);
        BridgeStoredResult stored = new BridgeStoredResult();
        stored.setRequestId("request-1");
        stored.setProcessStatus(BridgeProcessStatus.SUCCESS);
        stored.setWaitStatus(BridgeWaitStatus.WAITING);
        stored.setResultPayload("business-result");
        when(store.find("request-1")).thenReturn(java.util.Optional.of(stored));

        new BridgeCompletionCoordinator(registry, store).onCompletionNotification("request-1");

        ResponseEntity<?> response = (ResponseEntity<?>) deferred.getResult();
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("business-result", response.getBody());
        assertEquals(0, registry.size());
    }

    @Test
    void timeoutUsesPerRequestResponseHandler() {
        BridgeRequestStore store = mock(BridgeRequestStore.class);
        when(store.markTimedOutIfWaitingAndUnfinished(eq("request-2"), any())).thenReturn(true);
        TaskWaitRegistry registry = new TaskWaitRegistry(1);
        DeferredResult<ResponseEntity<String>> deferred = new DeferredResult<ResponseEntity<String>>();
        WaitContext<String> context = new WaitContext<String>("request-2", deferred, handler);
        registry.register(context);

        new BridgeCompletionCoordinator(registry, store).onTimeout(context);

        ResponseEntity<?> response = (ResponseEntity<?>) deferred.getResult();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("timeout:request-2", response.getBody());
    }

    @Test
    void submissionFailureUsesPerRequestResponseHandler() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BridgeRequestStore store = mock(BridgeRequestStore.class);
        BridgeMessagePublisher publisher = mock(BridgeMessagePublisher.class);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).send(any(), any(), any());
        BridgeRequestIdGenerator ids = () -> "request-3";
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        TaskWaitRegistry registry = new TaskWaitRegistry(1);
        DefaultServiceBridgeClient client = new DefaultServiceBridgeClient(
                new ServiceBridgeClientProperties(), ids, store, registry,
                mock(BridgeCompletionCoordinator.class), new BridgeResponseFactory(mapper),
                publisher, new BridgeTopicProperties(), transactions);

        DeferredResult<ResponseEntity<String>> deferred = client.request(
                "TEST", mapper.readTree("{}"), null, handler);

        ResponseEntity<?> response = (ResponseEntity<?>) deferred.getResult();
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("unavailable:request-3", response.getBody());
        assertEquals(0, registry.size());
    }
}
