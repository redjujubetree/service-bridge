package top.redjujubetree.servicebridge.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.redjujubetree.servicebridge.protocol.message.BridgeResponseMessage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonBridgeMessagePublisherTest {

    @Test
    void sendsRequestIdRawAndResponseAsJson() throws Exception {
        BridgeTransport transport = mock(BridgeTransport.class);
        JsonBridgeMessagePublisher publisher = new JsonBridgeMessagePublisher(
                transport, new ObjectMapper());

        publisher.send("request-topic", "request-1", "request-1");
        verify(transport).send("request-topic", "request-1", "request-1");

        BridgeResponseMessage response = new BridgeResponseMessage();
        response.setRequestId("request-1");
        publisher.send("response-topic", "request-1", response);
        ArgumentCaptor<String> responseJson = ArgumentCaptor.forClass(String.class);
        verify(transport).send(org.mockito.ArgumentMatchers.eq("response-topic"),
                org.mockito.ArgumentMatchers.eq("request-1"), responseJson.capture());
        assertEquals("request-1", new ObjectMapper().readTree(responseJson.getValue())
                .get("requestId").asText());
    }
}
