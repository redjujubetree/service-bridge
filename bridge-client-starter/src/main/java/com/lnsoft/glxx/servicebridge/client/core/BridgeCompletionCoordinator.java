package com.lnsoft.glxx.servicebridge.client.core;

import com.lnsoft.glxx.servicebridge.client.model.BridgeStoredResult;
import com.lnsoft.glxx.servicebridge.client.store.BridgeRequestStore;
import com.lnsoft.glxx.servicebridge.protocol.BridgeWaitStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Optional;

public class BridgeCompletionCoordinator {

    private final TaskWaitRegistry waitRegistry;
    private final BridgeRequestStore requestStore;
    private final BridgeResponseFactory responseFactory;

    public BridgeCompletionCoordinator(TaskWaitRegistry waitRegistry,
                                       BridgeRequestStore requestStore,
                                       BridgeResponseFactory responseFactory) {
        this.waitRegistry = waitRegistry;
        this.requestStore = requestStore;
        this.responseFactory = responseFactory;
    }

    public void onCompletionNotification(String requestId) {
        WaitContext context = waitRegistry.get(requestId);
        if (context == null || !context.isWaiting()) {
            return;
        }
        Optional<BridgeStoredResult> stored = requestStore.find(requestId);
        if (stored.isPresent() && stored.get().getProcessStatus().isTerminal()) {
            complete(context, stored.get());
        }
    }

    public void complete(WaitContext context, BridgeStoredResult stored) {
        if (!stored.getProcessStatus().isTerminal() || !context.tryClaim()) {
            return;
        }
        try {
            if (stored.getWaitStatus() == BridgeWaitStatus.TIMED_OUT) {
                context.finish(responseFactory.timeout(context.getRequestId()));
                return;
            }

            boolean responded = requestStore.markRespondedIfWaiting(
                    context.getRequestId(), LocalDateTime.now());
            if (!responded) {
                Optional<BridgeStoredResult> latest = requestStore.find(context.getRequestId());
                if (latest.isPresent() && latest.get().getWaitStatus() == BridgeWaitStatus.TIMED_OUT) {
                    context.finish(responseFactory.timeout(context.getRequestId()));
                    return;
                }
            }
            context.finish(responseFactory.fromStored(stored));
        } catch (RuntimeException e) {
            context.finish(responseFactory.unavailable(
                    context.getRequestId(), "Failed to finalize ServiceBridge response"));
        } finally {
            waitRegistry.remove(context.getRequestId(), context);
        }
    }

    public void onTimeout(WaitContext context) {
        if (!context.tryClaim()) {
            return;
        }
        try {
            boolean timedOut = requestStore.markTimedOutIfWaitingAndUnfinished(
                    context.getRequestId(), LocalDateTime.now());
            if (timedOut) {
                context.finish(responseFactory.timeout(context.getRequestId()));
                return;
            }

            Optional<BridgeStoredResult> stored = requestStore.find(context.getRequestId());
            if (stored.isPresent() && stored.get().getProcessStatus().isTerminal()
                    && stored.get().getWaitStatus() != BridgeWaitStatus.TIMED_OUT) {
                requestStore.markRespondedIfWaiting(context.getRequestId(), LocalDateTime.now());
                context.finish(responseFactory.fromStored(stored.get()));
            } else {
                context.finish(responseFactory.timeout(context.getRequestId()));
            }
        } catch (RuntimeException e) {
            context.finish(responseFactory.unavailable(
                    context.getRequestId(), "Failed to determine ServiceBridge timeout state"));
        } finally {
            waitRegistry.remove(context.getRequestId(), context);
        }
    }
}
