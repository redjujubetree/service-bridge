package com.lnsoft.erp.servicebridge.server.core;

import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.model.BridgeProcessingRecord;
import com.lnsoft.erp.servicebridge.server.store.BridgeResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class BridgeProcessingRecovery implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BridgeProcessingRecovery.class);

    private final BridgeResultStore resultStore;
    private final BridgeResultCommitter resultCommitter;
    private final ServiceBridgeServerProperties properties;

    public BridgeProcessingRecovery(BridgeResultStore resultStore,
                                    BridgeResultCommitter resultCommitter,
                                    ServiceBridgeServerProperties properties) {
        this.resultStore = resultStore;
        this.resultCommitter = resultCommitter;
        this.properties = properties;
    }

    @Override
    public void run() {
        List<BridgeProcessingRecord> expired = resultStore.findExpired(
                LocalDateTime.now(), properties.getRecoveryBatchSize());
        for (BridgeProcessingRecord record : expired) {
            try {
                resultCommitter.completeFailure(
                        record.getRequestId(),
                        record.getBusinessType(),
                        record.getProcessingToken(),
                        "PROCESSING_TIMEOUT",
                        "Server processing deadline exceeded");
            } catch (Exception e) {
                log.error("Failed to recover expired ServiceBridge request {}",
                        record.getRequestId(), e);
            }
        }
    }
}
