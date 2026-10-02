package top.redjujubetree.servicebridge.server.core;

import top.redjujubetree.servicebridge.server.model.BridgeProcessingRecord;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

public class BridgeProcessingRecovery {
    private final BridgeResultStore resultStore;
    private final BridgeResultCommitter committer;

    public BridgeProcessingRecovery(BridgeResultStore resultStore, BridgeResultCommitter committer) {
        this.resultStore = resultStore;
        this.committer = committer;
    }

    @Scheduled(fixedDelayString = "${service.bridge.server.recovery-interval-ms:5000}")
    public void recoverExpired() {
        for (BridgeProcessingRecord record : resultStore.findExpired(LocalDateTime.now(), 100)) {
            committer.completeExpiredFailure(
                    record.getRequestId(), record.getBusinessType(), record.getProcessingToken());
        }
    }
}
