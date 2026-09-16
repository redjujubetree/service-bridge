package com.lnsoft.erp.servicebridge.server.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.erp.servicebridge.protocol.BridgeRequestMessage;
import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import com.lnsoft.erp.servicebridge.server.core.BridgeRequestExecutor;
import top.redjujubetree.bedrock.mq.annotation.BedrockConsumer;
import top.redjujubetree.bedrock.mq.consumer.BedrockMessageConsumer;
import top.redjujubetree.bedrock.mq.entity.BedrockMessage;

@BedrockConsumer(
        value = ServiceBridgeProtocol.REQUEST_CONSUMER,
        topic = ServiceBridgeProtocol.REQUEST_TOPIC,
        maxRetry = 3)
public class BridgeRequestBedrockConsumer implements BedrockMessageConsumer {

    private final ObjectMapper objectMapper;
    private final BridgeRequestExecutor executor;

    public BridgeRequestBedrockConsumer(ObjectMapper objectMapper,
                                        BridgeRequestExecutor executor) {
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Override
    public void consume(BedrockMessage message) throws Exception {
        BridgeRequestMessage request = objectMapper.readValue(
                message.getPayload(), BridgeRequestMessage.class);
        executor.execute(request);
    }
}
