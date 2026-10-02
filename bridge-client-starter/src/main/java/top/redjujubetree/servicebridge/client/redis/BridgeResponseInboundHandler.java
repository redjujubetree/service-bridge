package top.redjujubetree.servicebridge.client.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.mq.BridgeInboundHandler;
import top.redjujubetree.servicebridge.protocol.validation.BridgeMessageValidator;
import top.redjujubetree.servicebridge.protocol.message.BridgeResponseMessage;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;

public class BridgeResponseInboundHandler implements BridgeInboundHandler {

    private final ObjectMapper objectMapper;
    private final RedisCompletionPublisher publisher;
    private final BridgeTopicProperties topics;

    public BridgeResponseInboundHandler(ObjectMapper objectMapper,
                                        RedisCompletionPublisher publisher,
                                        BridgeTopicProperties topics) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.topics = topics;
    }

    @Override
    public String topic() {
        return topics.getResponseTopic();
    }

    @Override
    public String consumerGroup() {
        return ServiceBridgeProtocol.RESPONSE_CONSUMER;
    }

    @Override
    public void handle(String payload) throws Exception {
        BridgeResponseMessage response = objectMapper.readValue(
                payload, BridgeResponseMessage.class);
        BridgeMessageValidator.validate(response);
        publisher.publish(response);
    }
}
