package com.lnsoft.glxx.servicebridge.protocol;

public class BridgeTopicProperties {

    private String requestTopic = ServiceBridgeProtocol.DEFAULT_REQUEST_TOPIC;
    private String responseTopic = ServiceBridgeProtocol.DEFAULT_RESPONSE_TOPIC;

    public String getRequestTopic() {
        return requestTopic;
    }

    public void setRequestTopic(String requestTopic) {
        this.requestTopic = requireTopic(requestTopic, "requestTopic");
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
        if (topic.indexOf(':') >= 0) {
            throw new IllegalArgumentException(name + " must not contain ':'");
        }
        return topic.trim();
    }
}
