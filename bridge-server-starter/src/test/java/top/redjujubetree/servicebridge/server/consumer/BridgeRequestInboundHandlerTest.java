package top.redjujubetree.servicebridge.server.consumer;

import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import top.redjujubetree.servicebridge.server.core.BridgeRequestExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BridgeRequestInboundHandlerTest {

    @Test
    void subscribesToDistinctTopicsAndPassesRequestId() throws Exception {
        BridgeTopicProperties topics = new BridgeTopicProperties();
        topics.getRequestTopics().put("ORDER", "orders.request");
        topics.getRequestTopics().put("PAYMENT", "payments.request");
        topics.getRequestTopics().put("REFUND", "orders.request");
        BridgeRequestExecutor executor = mock(BridgeRequestExecutor.class);
        BridgeRequestInboundHandler inbound = new BridgeRequestInboundHandler(executor, topics);

        assertEquals(2, inbound.topics().size());
        assertTrue(inbound.topics().contains("orders.request"));
        assertTrue(inbound.topics().contains("payments.request"));
        assertTrue(!inbound.consumerGroup("orders.request")
                .equals(inbound.consumerGroup("payments.request")));
        assertThrows(IllegalArgumentException.class,
                () -> inbound.handle("orders.request", " "));
        verifyNoInteractions(executor);

        inbound.handle("orders.request", "request-1");
        verify(executor).execute("request-1", "orders.request");
    }
}
