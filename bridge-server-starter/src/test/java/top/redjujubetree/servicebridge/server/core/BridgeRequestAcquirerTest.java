package top.redjujubetree.servicebridge.server.core;

import top.redjujubetree.servicebridge.server.config.ServiceBridgeServerProperties;
import top.redjujubetree.servicebridge.server.model.AcquireResult;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeRequestAcquirerTest {
    @Test
    void acquiresWithoutMqDependency() {
        BridgeResultStore store = mock(BridgeResultStore.class);
        LocalDateTime now = LocalDateTime.now();
        when(store.tryAcquire(eq("request-1"), eq("token-a"), eq(now), any()))
                .thenReturn(AcquireResult.ACQUIRED);

        assertEquals(AcquireResult.ACQUIRED,
                new BridgeRequestAcquirer(store, new ServiceBridgeServerProperties())
                        .acquire("request-1", "token-a", now));
    }
}
