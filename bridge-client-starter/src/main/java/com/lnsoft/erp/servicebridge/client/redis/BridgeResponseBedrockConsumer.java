package com.lnsoft.erp.servicebridge.client.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.erp.servicebridge.protocol.BridgeMessageValidator;
import com.lnsoft.erp.servicebridge.protocol.BridgeResponseMessage;
import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import top.redjujubetree.bedrock.mq.annotation.BedrockConsumer;
import top.redjujubetree.bedrock.mq.consumer.BedrockMessageConsumer;
import top.redjujubetree.bedrock.mq.entity.BedrockMessage;

@BedrockConsumer(
        value = ServiceBridgeProtocol.RESPONSE_CONSUMER,
        topic = ServiceBridgeProtocol.RESPONSE_TOPIC,
        maxRetry = 10)
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
