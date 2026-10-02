package top.redjujubetree.servicebridge.protocol;

public final class ServiceBridgeProtocol {

    public static final String VERSION = "1.0";

    public static final String DEFAULT_REQUEST_TOPIC = "service-bridge.request";
    public static final String DEFAULT_RESPONSE_TOPIC = "service-bridge.response";

    public static final String REQUEST_CONSUMER = "service-bridge-server";
    public static final String RESPONSE_CONSUMER = "service-bridge-client";

    private ServiceBridgeProtocol() {
    }
}
