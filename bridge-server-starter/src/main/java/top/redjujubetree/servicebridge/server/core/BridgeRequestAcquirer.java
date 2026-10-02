package top.redjujubetree.servicebridge.server.core;

import top.redjujubetree.servicebridge.server.config.ServiceBridgeServerProperties;
import top.redjujubetree.servicebridge.server.model.AcquireResult;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public class BridgeRequestAcquirer {

    private final BridgeResultStore resultStore;
    private final ServiceBridgeServerProperties properties;

    public BridgeRequestAcquirer(BridgeResultStore resultStore,
                                 ServiceBridgeServerProperties properties) {
        this.resultStore = resultStore;
        this.properties = properties;
    }

    @Transactional
    public AcquireResult acquire(String requestId, String attemptToken, LocalDateTime startedAt) {
        return resultStore.tryAcquire(requestId, attemptToken, startedAt,
                startedAt.plusNanos(properties.getProcessingTimeout().toNanos()));
    }

    @Transactional
    public void releaseForRetry(String requestId, String attemptToken) {
        resultStore.releaseForRetry(requestId, attemptToken, LocalDateTime.now());
    }
}
