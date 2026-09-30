package com.lnsoft.glxx.servicebridge.server.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.glxx.servicebridge.server.core.BridgeProcessingRecovery;
import com.lnsoft.glxx.servicebridge.server.model.BridgeRecoveryMessage;
import top.redjujubetree.bedrock.mq.consumer.BedrockMessageConsumer;
import top.redjujubetree.bedrock.mq.entity.BedrockMessage;

public class BridgeRecoveryBedrockConsumer implements BedrockMessageConsumer {

    private final ObjectMapper objectMapper;
    private final BridgeProcessingRecovery recovery;

    public BridgeRecoveryBedrockConsumer(ObjectMapper objectMapper,
                                         BridgeProcessingRecovery recovery) {
        this.objectMapper = objectMapper;
        this.recovery = recovery;
    }

    @Override
    public void consume(BedrockMessage message) throws Exception {
        recovery.recover(objectMapper.readValue(message.getPayload(), BridgeRecoveryMessage.class));
    }
}
