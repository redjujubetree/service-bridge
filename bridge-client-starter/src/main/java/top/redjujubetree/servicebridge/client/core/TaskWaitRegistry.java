package top.redjujubetree.servicebridge.client.core;

import top.redjujubetree.servicebridge.client.exception.TooManyWaitingRequestsException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

public class TaskWaitRegistry {

    private final ConcurrentMap<String, WaitContext<?>> waiting =
            new ConcurrentHashMap<String, WaitContext<?>>();
    private final Semaphore capacity;

    public TaskWaitRegistry(int maxWaitingRequests) {
        if (maxWaitingRequests <= 0) {
            throw new IllegalArgumentException("maxWaitingRequests must be greater than zero");
        }
        this.capacity = new Semaphore(maxWaitingRequests);
    }

    public void register(WaitContext<?> context) {
        if (!capacity.tryAcquire()) {
            throw new TooManyWaitingRequestsException("Too many waiting ServiceBridge requests");
        }
        WaitContext<?> previous = waiting.putIfAbsent(context.getRequestId(), context);
        if (previous != null) {
            capacity.release();
            throw new IllegalStateException("Duplicate requestId: " + context.getRequestId());
        }
    }

    public WaitContext<?> get(String requestId) {
        return waiting.get(requestId);
    }

    public boolean remove(String requestId, WaitContext<?> context) {
        boolean removed = waiting.remove(requestId, context);
        if (removed) {
            capacity.release();
        }
        return removed;
    }

    public Collection<WaitContext<?>> snapshot() {
        return new ArrayList<WaitContext<?>>(waiting.values());
    }

    public int size() {
        return waiting.size();
    }
}
