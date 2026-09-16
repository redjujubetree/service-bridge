package com.lnsoft.erp.servicebridge.client.core;

import com.lnsoft.erp.servicebridge.client.model.BridgeHttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitContextTest {

    @Test
    void onlyOneThreadCanClaimCompletion() throws Exception {
        DeferredResult<ResponseEntity<BridgeHttpResponse>> deferred = new DeferredResult<ResponseEntity<BridgeHttpResponse>>();
        WaitContext context = new WaitContext("request-1", deferred);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<Runnable>();
        for (int i = 0; i < 32; i++) {
            tasks.add(() -> {
                try {
                    start.await();
                    if (context.tryClaim()) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        for (Runnable task : tasks) {
            executor.submit(task);
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, winners.get());
    }

    @Test
    void registryEnforcesCapacityAndReleasesPermit() {
        TaskWaitRegistry registry = new TaskWaitRegistry(1);
        WaitContext first = context("first");
        registry.register(first);

        org.junit.jupiter.api.Assertions.assertThrows(
                TooManyWaitingRequestsException.class,
                () -> registry.register(context("second")));

        assertTrue(registry.remove("first", first));
        registry.register(context("second"));
        assertEquals(1, registry.size());
    }

    private WaitContext context(String requestId) {
        return new WaitContext(
                requestId,
                new DeferredResult<ResponseEntity<BridgeHttpResponse>>());
    }
}
