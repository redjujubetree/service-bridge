package com.lnsoft.erp.servicebridge.server.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

public class BridgeProcessingRecoveryScheduler implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BridgeProcessingRecoveryScheduler.class);

    private final ThreadPoolTaskScheduler scheduler;
    private final BridgeProcessingRecovery recovery;
    private final Duration interval;
    private ScheduledFuture<?> future;

    public BridgeProcessingRecoveryScheduler(ThreadPoolTaskScheduler scheduler,
                                             BridgeProcessingRecovery recovery,
                                             Duration interval) {
        this.scheduler = scheduler;
        this.recovery = recovery;
        this.interval = interval;
    }

    @Override
    public void afterPropertiesSet() {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("recoveryInterval must be greater than zero");
        }
        future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                recovery.run();
            } catch (Exception e) {
                log.error("ServiceBridge server recovery failed", e);
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
