package top.redjujubetree.servicebridge.client.core;

import top.redjujubetree.servicebridge.protocol.BridgeTopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BridgeTopicPropertiesBindingTest {

    @Test
    void preservesBracketedBusinessType() {
        BridgeTopicProperties topics = new Binder(new MapConfigurationPropertySource(
                Collections.singletonMap("service.bridge.request-topics.[ORDER]", "orders.request")))
                .bind("service.bridge", BridgeTopicProperties.class).get();

        assertEquals("orders.request", topics.requestTopicFor("ORDER"));
    }
}
