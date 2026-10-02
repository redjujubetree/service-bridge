package top.redjujubetree.servicebridge.mq;

/** Sends one message and returns only after the broker confirms it. */
public interface BridgeTransport {
    void send(String topic, String key, String payload);
}
