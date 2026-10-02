package top.redjujubetree.servicebridge.mq;

import java.util.Collections;
import java.util.Collection;

public interface BridgeInboundHandler {
    String topic();

    default Collection<String> topics() {
        return Collections.singleton(topic());
    }

    String consumerGroup();

    default String consumerGroup(String topic) {
        return consumerGroup();
    }

    void handle(String payload) throws Exception;

    default void handle(String topic, String payload) throws Exception {
        handle(payload);
    }
}
