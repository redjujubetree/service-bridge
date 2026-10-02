package top.redjujubetree.servicebridge.server.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.mq.BridgeInboundHandler;
import top.redjujubetree.servicebridge.protocol.message.BridgeRequestMessage;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;
import top.redjujubetree.servicebridge.server.core.BridgeRequestExecutor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

public class BridgeRequestInboundHandler implements BridgeInboundHandler {

    private final ObjectMapper objectMapper;
    private final BridgeRequestExecutor executor;
    private final BridgeTopicProperties topics;

    public BridgeRequestInboundHandler(ObjectMapper objectMapper,
                                       BridgeRequestExecutor executor,
                                       BridgeTopicProperties topics) {
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.topics = topics;
    }

    @Override
    public String topic() {
        return topics.getRequestTopic();
    }

    @Override
    public Collection<String> topics() {
        return topics.requestTopicNames();
    }

    @Override
    public String consumerGroup() {
        return ServiceBridgeProtocol.REQUEST_CONSUMER;
    }

    @Override
    public String consumerGroup(String topic) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(topic.getBytes(StandardCharsets.UTF_8));
            StringBuilder group = new StringBuilder(ServiceBridgeProtocol.REQUEST_CONSUMER).append('-');
            for (int i = 0; i < 16; i++) {
                byte value = digest[i];
                group.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                group.append(Character.forDigit(value & 0x0f, 16));
            }
            return group.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Override
    public void handle(String payload) throws Exception {
        handle(topic(), payload);
    }

    @Override
    public void handle(String topic, String payload) throws Exception {
        BridgeRequestMessage request = objectMapper.readValue(
                payload, BridgeRequestMessage.class);
        if (!topic.equals(topics.requestTopicFor(request.getBusinessType()))) {
            throw new IllegalArgumentException("businessType " + request.getBusinessType()
                    + " is not configured for REQUEST topic " + topic);
        }
        executor.execute(request);
    }
}
