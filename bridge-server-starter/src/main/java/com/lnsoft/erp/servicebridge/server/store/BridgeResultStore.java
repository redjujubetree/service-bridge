package com.lnsoft.erp.servicebridge.server.store;

import com.lnsoft.erp.servicebridge.server.model.AcquireResult;
import com.lnsoft.erp.servicebridge.server.model.BridgeProcessingRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BridgeResultStore {

    AcquireResult tryAcquire(String requestId,
                             String attemptToken,
                             LocalDateTime startedAt,
                             LocalDateTime expiresAt);

    boolean completeSuccess(String requestId,
                            String attemptToken,
                            String resultPayload,
                            LocalDateTime completedAt);

    boolean completeFailure(String requestId,
                            String attemptToken,
                            String errorCode,
                            String errorMessage,
                            LocalDateTime completedAt);

    Optional<BridgeProcessingRecord> find(String requestId);

    List<BridgeProcessingRecord> findExpired(LocalDateTime now, int limit);
}
