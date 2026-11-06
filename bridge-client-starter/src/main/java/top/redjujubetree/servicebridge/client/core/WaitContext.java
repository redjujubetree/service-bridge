package top.redjujubetree.servicebridge.client.core;

import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class WaitContext<T> {

    private final String requestId;
    private final DeferredResult<ResponseEntity<T>> deferredResult;
    private final BridgeResponseHandler<T> responseHandler;
    private final Instant createdAt = Instant.now();
    private final AtomicReference<State> state = new AtomicReference<State>(State.WAITING);

    public WaitContext(String requestId,
                       DeferredResult<ResponseEntity<T>> deferredResult,
                       BridgeResponseHandler<T> responseHandler) {
        this.requestId = requestId;
        this.deferredResult = deferredResult;
        this.responseHandler = responseHandler;
    }

    public boolean tryClaim() {
        return state.compareAndSet(State.WAITING, State.COMPLETING);
    }

    public boolean finish(ResponseEntity<T> response) {
        if (state.get() != State.COMPLETING) {
            return false;
        }
        boolean accepted = deferredResult.setResult(response);
        state.set(State.COMPLETED);
        return accepted;
    }

    public boolean tryComplete(ResponseEntity<T> response) {
        return tryClaim() && finish(response);
    }

    public boolean finishError(Throwable error) {
        if (state.get() != State.COMPLETING) {
            return false;
        }
        boolean accepted = deferredResult.setErrorResult(error);
        state.set(State.COMPLETED);
        return accepted;
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

    public DeferredResult<ResponseEntity<T>> getDeferredResult() {
        return deferredResult;
    }

    public BridgeResponseHandler<T> getResponseHandler() {
        return responseHandler;
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
