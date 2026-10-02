package top.redjujubetree.servicebridge.server.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeRequestHandlerRegistryTest {

    @Test
    void registersAndFindsHandler() {
        BridgeRequestHandler handler = handler("TEST");
        BridgeRequestHandlerRegistry registry =
                new BridgeRequestHandlerRegistry(Collections.singletonList(handler));
        assertEquals(handler, registry.findRequired("TEST"));
    }

    @Test
    void rejectsDuplicateBusinessType() {
        assertThrows(IllegalStateException.class, () ->
                new BridgeRequestHandlerRegistry(Arrays.asList(handler("TEST"), handler("TEST"))));
    }

    private BridgeRequestHandler handler(final String businessType) {
        return new BridgeRequestHandler() {
            @Override
            public String businessType() {
                return businessType;
            }

            @Override
            public BridgeHandlerResult handle(BridgeRequestContext context) {
                return BridgeHandlerResult.success(null);
            }
        };
    }
}
