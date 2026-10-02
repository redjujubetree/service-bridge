package top.redjujubetree.servicebridge.protocol.config;

import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class BridgeTopicProperties {

    private String requestTopic = ServiceBridgeProtocol.DEFAULT_REQUEST_TOPIC;
    private Map<String, String> requestTopics = new LinkedHashMap<>();
    private String responseTopic = ServiceBridgeProtocol.DEFAULT_RESPONSE_TOPIC;

    public String getRequestTopic() {
        return requestTopic;
    }

    public void setRequestTopic(String requestTopic) {
        this.requestTopic = requireTopic(requestTopic, "requestTopic");
    }

    public Map<String, String> getRequestTopics() {
        return requestTopics;
    }

    public void setRequestTopics(Map<String, String> requestTopics) {
        this.requestTopics = requestTopics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestTopics);
    }

    public String requestTopicFor(String businessType) {
        if (requestTopics.isEmpty()) {
            return requestTopic;
        }
        validateRequestTopics();
        String topic = requestTopics.get(businessType);
        if (topic == null) {
            throw new IllegalArgumentException("No REQUEST topic configured for businessType: " + businessType);
        }
        return topic.trim();
    }

    public Collection<String> requestTopicNames() {
        if (requestTopics.isEmpty()) {
            return java.util.Collections.singleton(requestTopic);
        }
        validateRequestTopics();
        Collection<String> topics = new LinkedHashSet<>();
        for (String topic : requestTopics.values()) {
            topics.add(topic.trim());
        }
        return new ArrayList<>(topics);
    }

    private void validateRequestTopics() {
        for (Map.Entry<String, String> entry : requestTopics.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()
                    || !entry.getKey().equals(entry.getKey().trim())) {
                throw new IllegalArgumentException("requestTopics businessType must not be blank or padded");
            }
            requireTopic(entry.getValue(), "requestTopics[" + entry.getKey() + "]");
        }
    }

    public String getResponseTopic() {
        return responseTopic;
    }

    public void setResponseTopic(String responseTopic) {
        this.responseTopic = requireTopic(responseTopic, "responseTopic");
    }

    private String requireTopic(String topic, String name) {
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return topic.trim();
    }
}
