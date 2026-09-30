package com.lnsoft.glxx.servicebridge.client.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

public class BridgeReconciliationScheduler implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BridgeReconciliationScheduler.class);

    private final ThreadPoolTaskScheduler scheduler;
    private final BridgeResultReconciler reconciler;
    private final Duration interval;
    private ScheduledFuture<?> future;

    public BridgeReconciliationScheduler(ThreadPoolTaskScheduler scheduler,
                                         BridgeResultReconciler reconciler,
                                         Duration interval) {
        this.scheduler = scheduler;
        this.reconciler = reconciler;
        this.interval = interval;
    }

    @Override
    public void afterPropertiesSet() {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("reconciliationInterval must be greater than zero");
        }
        future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                reconciler.run();
            } catch (Exception e) {
                log.error("ServiceBridge reconciliation failed", e);
            }
        }, interval.toMillis());
    }

    @Override
    public void destroy() {
        if (future != null) {
            future.cancel(false);
        }
    }
}
