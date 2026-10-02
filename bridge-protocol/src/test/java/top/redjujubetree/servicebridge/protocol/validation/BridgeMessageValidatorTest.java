package top.redjujubetree.servicebridge.protocol.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;
import top.redjujubetree.servicebridge.protocol.message.BridgeRequestMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeMessageValidatorTest {

    @Test
    void validatesRequestProtocolAndRequiredFields() throws Exception {
        BridgeRequestMessage message = new BridgeRequestMessage();
        message.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        message.setMessageId("message-1");
        message.setRequestId("request-1");
        message.setBusinessType("TEST");
        message.setPayload(new ObjectMapper().readTree("{\"value\":1}"));

        assertDoesNotThrow(() -> BridgeMessageValidator.validate(message));

        message.setProtocolVersion("2.0");
        assertThrows(BridgeProtocolException.class, () -> BridgeMessageValidator.validate(message));
    }
}
