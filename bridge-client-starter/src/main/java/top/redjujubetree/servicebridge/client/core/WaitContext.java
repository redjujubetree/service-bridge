package top.redjujubetree.servicebridge.client.core;

import top.redjujubetree.servicebridge.client.model.BridgeHttpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class WaitContext {

    private final String requestId;
    private final DeferredResult<ResponseEntity<BridgeHttpResponse>> deferredResult;
    private final Instant createdAt = Instant.now();
    private final AtomicReference<State> state = new AtomicReference<State>(State.WAITING);

    public WaitContext(String requestId,
                       DeferredResult<ResponseEntity<BridgeHttpResponse>> deferredResult) {
        this.requestId = requestId;
        this.deferredResult = deferredResult;
    }

    public boolean tryClaim() {
        return state.compareAndSet(State.WAITING, State.COMPLETING);
    }

    public boolean finish(ResponseEntity<BridgeHttpResponse> response) {
        if (state.get() != State.COMPLETING) {
            return false;
        }
        boolean accepted = deferredResult.setResult(response);
        state.set(State.COMPLETED);
        return accepted;
    }

    public boolean tryComplete(ResponseEntity<BridgeHttpResponse> response) {
        return tryClaim() && finish(response);
    }

    public boolean cancel() {
        return state.compareAndSet(State.WAITING, State.CANCELLED);
    }

    public boolean isWaiting() {
        return state.get() == State.WAITING;
    }

    public String getRequestId() {
        return requestId;
    }

    public DeferredResult<ResponseEntity<BridgeHttpResponse>> getDeferredResult() {
        return deferredResult;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public State getState() {
        return state.get();
    }

    public enum State {
        WAITING,
        COMPLETING,
        COMPLETED,
        CANCELLED
    }
}
