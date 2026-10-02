package top.redjujubetree.servicebridge.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonBridgeMessagePublisher implements BridgeMessagePublisher {
    private final BridgeTransport transport;
    private final ObjectMapper mapper;

    public JsonBridgeMessagePublisher(BridgeTransport transport, ObjectMapper mapper) {
        this.transport = transport;
        this.mapper = mapper;
    }

    @Override
    public void send(String topic, String requestId, Object message) {
        try {
            transport.send(topic, requestId, mapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize bridge message", e);
        }
    }
}
