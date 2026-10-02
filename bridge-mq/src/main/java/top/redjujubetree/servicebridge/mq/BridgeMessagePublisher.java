package top.redjujubetree.servicebridge.mq;

/** Serializes one protocol message and waits for the selected MQ to confirm it. */
public interface BridgeMessagePublisher {
    void send(String topic, String requestId, Object message);
}
