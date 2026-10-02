package top.redjujubetree.servicebridge.mq.bedrock;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class BedrockMessageSourceTest {

    @Test
    void usesApplicationNameByDefault() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "orders-client");

        assertEquals("orders-client", BedrockBridgeAutoConfiguration.resolveMessageSource(environment));
    }

    @Test
    void explicitSourceOverridesApplicationName() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "orders-client")
                .withProperty("service.bridge.mq.bedrock.message-source", "orders-bridge");

        assertEquals("orders-bridge", BedrockBridgeAutoConfiguration.resolveMessageSource(environment));
    }

    @Test
    void missingSourceAndApplicationNameUsesStarterDefault() {
        assertEquals("bridge-client-starter",
                BedrockBridgeAutoConfiguration.resolveMessageSource(mock(Environment.class)));
    }
}
