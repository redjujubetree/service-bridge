package top.redjujubetree.servicebridge.client.core;

import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BridgeResultReconciler implements Runnable {

    private final TaskWaitRegistry registry;
    private final BridgeRequestStore requestStore;
    private final BridgeCompletionCoordinator coordinator;

    public BridgeResultReconciler(TaskWaitRegistry registry,
                                  BridgeRequestStore requestStore,
                                  BridgeCompletionCoordinator coordinator) {
        this.registry = registry;
        this.requestStore = requestStore;
        this.coordinator = coordinator;
    }

    @Override
    public void run() {
        Collection<WaitContext<?>> contexts = registry.snapshot();
        if (contexts.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<String>(contexts.size());
        for (WaitContext<?> context : contexts) {
            if (context.isWaiting()) {
                ids.add(context.getRequestId());
            }
        }
        Map<String, BridgeStoredResult> terminal = requestStore.findTerminal(ids);
        for (WaitContext<?> context : contexts) {
            BridgeStoredResult stored = terminal.get(context.getRequestId());
            if (stored != null) {
                coordinator.complete(context, stored);
            }
        }
    }
}
