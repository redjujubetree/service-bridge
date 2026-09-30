package com.lnsoft.glxx.servicebridge.client.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.glxx.servicebridge.client.config.ServiceBridgeClientProperties;
import com.lnsoft.glxx.servicebridge.protocol.BridgeResponseMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisCompletionPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ServiceBridgeClientProperties properties;

    public RedisCompletionPublisher(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    ServiceBridgeClientProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(BridgeResponseMessage message) {
        try {
            redisTemplate.convertAndSend(
                    properties.getRedisChannel(),
                    objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize ServiceBridge completion message", e);
        }
    }
}
