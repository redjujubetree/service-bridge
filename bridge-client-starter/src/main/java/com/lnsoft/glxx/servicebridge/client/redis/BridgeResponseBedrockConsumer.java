package com.lnsoft.glxx.servicebridge.client.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.glxx.servicebridge.protocol.BridgeMessageValidator;
import com.lnsoft.glxx.servicebridge.protocol.BridgeResponseMessage;
import top.redjujubetree.bedrock.mq.consumer.BedrockMessageConsumer;
import top.redjujubetree.bedrock.mq.entity.BedrockMessage;

public class BridgeResponseBedrockConsumer implements BedrockMessageConsumer {

    private final ObjectMapper objectMapper;
    private final RedisCompletionPublisher publisher;

    public BridgeResponseBedrockConsumer(ObjectMapper objectMapper,
                                         RedisCompletionPublisher publisher) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @Override
    public void consume(BedrockMessage message) throws Exception {
        BridgeResponseMessage response = objectMapper.readValue(
                message.getPayload(), BridgeResponseMessage.class);
        BridgeMessageValidator.validate(response);
        publisher.publish(response);
    }
}
