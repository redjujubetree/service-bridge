package top.redjujubetree.servicebridge.server.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.protocol.BridgeRequestMessage;
import top.redjujubetree.servicebridge.protocol.BridgeTopicProperties;
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
    void subscribesToDistinctTopicsAndChecksBusinessRoute() throws Exception {
        BridgeTopicProperties topics = new BridgeTopicProperties();
        topics.getRequestTopics().put("ORDER", "orders.request");
        topics.getRequestTopics().put("PAYMENT", "payments.request");
        topics.getRequestTopics().put("REFUND", "orders.request");
        BridgeRequestExecutor executor = mock(BridgeRequestExecutor.class);
        BridgeRequestInboundHandler inbound = new BridgeRequestInboundHandler(
                new ObjectMapper(), executor, topics);
        String order = "{\"businessType\":\"ORDER\"}";

        assertEquals(2, inbound.topics().size());
        assertTrue(inbound.topics().contains("orders.request"));
        assertTrue(inbound.topics().contains("payments.request"));
        assertTrue(!inbound.consumerGroup("orders.request")
                .equals(inbound.consumerGroup("payments.request")));
        assertThrows(IllegalArgumentException.class,
                () -> inbound.handle("payments.request", order));
        verifyNoInteractions(executor);

        inbound.handle("orders.request", order);
        verify(executor).execute(any(BridgeRequestMessage.class));
    }
}
