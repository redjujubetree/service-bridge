package top.redjujubetree.servicebridge.server.store;

import top.redjujubetree.servicebridge.server.model.AcquireResult;
import top.redjujubetree.servicebridge.server.model.BridgeProcessingRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BridgeResultStore {

    AcquireResult tryAcquire(String requestId,
                             String attemptToken,
                             LocalDateTime startedAt,
                             LocalDateTime expiresAt);

    boolean releaseForRetry(String requestId, String attemptToken, LocalDateTime updatedAt);

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

    List<BridgeProcessingRecord> findExpired(LocalDateTime now, int limit);

}
