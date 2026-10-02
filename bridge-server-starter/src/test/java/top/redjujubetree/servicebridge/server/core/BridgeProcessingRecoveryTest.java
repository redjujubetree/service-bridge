package top.redjujubetree.servicebridge.server.core;

import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;
import top.redjujubetree.servicebridge.server.model.BridgeProcessingRecord;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeProcessingRecoveryTest {
    @Test
    void expiredLeaseUsesConditionalCompletion() {
        BridgeResultStore store = mock(BridgeResultStore.class);
        BridgeResultCommitter committer = mock(BridgeResultCommitter.class);
        BridgeProcessingRecord record = new BridgeProcessingRecord();
        record.setRequestId("request-1");
        record.setBusinessType("TEST");
        record.setProcessingToken("token-a");
        record.setProcessStatus(BridgeProcessStatus.PROCESSING);
        when(store.findExpired(any(LocalDateTime.class), eq(100)))
                .thenReturn(Collections.singletonList(record));

        new BridgeProcessingRecovery(store, committer).recoverExpired();

        verify(committer).completeExpiredFailure("request-1", "TEST", "token-a");
    }
}
