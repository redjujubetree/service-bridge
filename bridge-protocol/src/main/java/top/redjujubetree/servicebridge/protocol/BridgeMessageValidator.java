package top.redjujubetree.servicebridge.protocol;

public final class BridgeMessageValidator {

    private BridgeMessageValidator() {
    }

    public static void validate(BridgeRequestMessage message) {
        if (message == null) {
            throw new BridgeProtocolException("Request message must not be null");
        }
        validateVersion(message.getProtocolVersion());
        requireText(message.getMessageId(), "messageId");
        requireText(message.getRequestId(), "requestId");
        requireText(message.getBusinessType(), "businessType");
        if (message.getPayload() == null || message.getPayload().isNull()) {
            throw new BridgeProtocolException("payload must not be null");
        }
    }

    public static void validate(BridgeResponseMessage message) {
        if (message == null) {
            throw new BridgeProtocolException("Response message must not be null");
        }
        validateVersion(message.getProtocolVersion());
        requireText(message.getMessageId(), "messageId");
        requireText(message.getRequestId(), "requestId");
        requireText(message.getBusinessType(), "businessType");
        if (message.getProcessStatus() == null || !message.getProcessStatus().isTerminal()) {
            throw new BridgeProtocolException("Response processStatus must be SUCCESS or FAILED");
        }
    }

    private static void validateVersion(String version) {
        if (!ServiceBridgeProtocol.VERSION.equals(version)) {
            throw new BridgeProtocolException("Unsupported protocol version: " + version);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BridgeProtocolException(field + " must not be blank");
        }
    }
}
