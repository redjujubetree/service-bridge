package top.redjujubetree.servicebridge.client.core;

import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;
import top.redjujubetree.servicebridge.protocol.status.BridgeWaitStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Optional;

public class BridgeCompletionCoordinator {

    private final TaskWaitRegistry waitRegistry;
    private final BridgeRequestStore requestStore;
    public BridgeCompletionCoordinator(TaskWaitRegistry waitRegistry,
                                       BridgeRequestStore requestStore) {
        this.waitRegistry = waitRegistry;
        this.requestStore = requestStore;
    }

    public void onCompletionNotification(String requestId) {
        WaitContext<?> context = waitRegistry.get(requestId);
        if (context == null || !context.isWaiting()) {
            return;
        }
        Optional<BridgeStoredResult> stored = requestStore.find(requestId);
        if (stored.isPresent() && stored.get().getProcessStatus().isTerminal()) {
            complete(context, stored.get());
        }
    }

    public <T> void complete(WaitContext<T> context, BridgeStoredResult stored) {
        if (!stored.getProcessStatus().isTerminal() || !context.tryClaim()) {
            return;
        }
        try {
            if (stored.getWaitStatus() == BridgeWaitStatus.TIMED_OUT) {
                context.finish(context.getResponseHandler().timeout(context.getRequestId()));
                return;
            }

            boolean responded = requestStore.markRespondedIfWaiting(
                    context.getRequestId(), LocalDateTime.now());
            if (!responded) {
                Optional<BridgeStoredResult> latest = requestStore.find(context.getRequestId());
                if (latest.isPresent() && latest.get().getWaitStatus() == BridgeWaitStatus.TIMED_OUT) {
                    context.finish(context.getResponseHandler().timeout(context.getRequestId()));
                    return;
                }
            }
            context.finish(context.getResponseHandler().fromStored(stored));
        } catch (RuntimeException e) {
            finishUnavailable(context, "Failed to finalize ServiceBridge response");
        } finally {
            waitRegistry.remove(context.getRequestId(), context);
        }
    }

    public <T> void onTimeout(WaitContext<T> context) {
        if (!context.tryClaim()) {
            return;
        }
        try {
            boolean timedOut = requestStore.markTimedOutIfWaitingAndUnfinished(
                    context.getRequestId(), LocalDateTime.now());
            if (timedOut) {
                context.finish(context.getResponseHandler().timeout(context.getRequestId()));
                return;
            }

            Optional<BridgeStoredResult> stored = requestStore.find(context.getRequestId());
            if (stored.isPresent() && stored.get().getProcessStatus().isTerminal()
                    && stored.get().getWaitStatus() != BridgeWaitStatus.TIMED_OUT) {
                requestStore.markRespondedIfWaiting(context.getRequestId(), LocalDateTime.now());
                context.finish(context.getResponseHandler().fromStored(stored.get()));
            } else {
                context.finish(context.getResponseHandler().timeout(context.getRequestId()));
            }
        } catch (RuntimeException e) {
            finishUnavailable(context, "Failed to determine ServiceBridge timeout state");
        } finally {
            waitRegistry.remove(context.getRequestId(), context);
        }
    }

    private <T> void finishUnavailable(WaitContext<T> context, String message) {
        try {
            context.finish(context.getResponseHandler().unavailable(context.getRequestId(), message));
        } catch (RuntimeException handlerFailure) {
            context.finishError(handlerFailure);
        }
    }
}
