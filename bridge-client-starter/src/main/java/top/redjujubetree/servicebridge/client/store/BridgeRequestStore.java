package top.redjujubetree.servicebridge.client.store;

import top.redjujubetree.servicebridge.client.model.BridgeRequestRecord;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface BridgeRequestStore {

    void create(BridgeRequestRecord request);

    boolean markSubmissionFailed(String requestId, String errorMessage, LocalDateTime failedAt);

    Optional<BridgeStoredResult> find(String requestId);

    boolean markRespondedIfWaiting(String requestId, LocalDateTime respondedAt);

    boolean markTimedOutIfWaitingAndUnfinished(String requestId, LocalDateTime timedOutAt);

    default Map<String, BridgeStoredResult> findTerminal(Collection<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, BridgeStoredResult> result = new LinkedHashMap<String, BridgeStoredResult>();
        for (String requestId : requestIds) {
            Optional<BridgeStoredResult> stored = find(requestId);
            if (stored.isPresent() && stored.get().getProcessStatus().isTerminal()) {
                result.put(requestId, stored.get());
            }
        }
        return result;
    }
}
