package com.lnsoft.glxx.servicebridge.client.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.glxx.servicebridge.client.core.BridgeCompletionCoordinator;
import com.lnsoft.glxx.servicebridge.protocol.BridgeMessageValidator;
import com.lnsoft.glxx.servicebridge.protocol.BridgeResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

public class RedisCompletionListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisCompletionListener.class);

    private final ObjectMapper objectMapper;
    private final BridgeCompletionCoordinator coordinator;

    public RedisCompletionListener(ObjectMapper objectMapper,
                                   BridgeCompletionCoordinator coordinator) {
        this.objectMapper = objectMapper;
        this.coordinator = coordinator;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            BridgeResponseMessage response = objectMapper.readValue(json, BridgeResponseMessage.class);
            BridgeMessageValidator.validate(response);
            coordinator.onCompletionNotification(response.getRequestId());
        } catch (Exception e) {
            log.error("Failed to process ServiceBridge Redis completion notification", e);
        }
    }
}
