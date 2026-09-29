package com.lnsoft.erp.servicebridge.server.store;

import com.lnsoft.erp.servicebridge.server.model.AcquireResult;
import com.lnsoft.erp.servicebridge.server.model.BridgeProcessingRecord;

import java.time.LocalDateTime;
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

    boolean completeExpiredFailure(String requestId,
                                   String attemptToken,
                                   String errorCode,
                                   String errorMessage,
                                   LocalDateTime completedAt);

    boolean isClientWaiting(String requestId, LocalDateTime now);

    Optional<BridgeProcessingRecord> find(String requestId);

}
