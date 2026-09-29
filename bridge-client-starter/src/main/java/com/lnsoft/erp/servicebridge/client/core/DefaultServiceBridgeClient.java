package com.lnsoft.erp.servicebridge.client.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.lnsoft.erp.servicebridge.client.config.ServiceBridgeClientProperties;
import com.lnsoft.erp.servicebridge.client.model.BridgeHttpResponse;
import com.lnsoft.erp.servicebridge.client.model.BridgeRequestRecord;
import com.lnsoft.erp.servicebridge.client.model.BridgeStoredResult;
import com.lnsoft.erp.servicebridge.client.store.BridgeRequestStore;
import com.lnsoft.erp.servicebridge.protocol.BridgeProcessStatus;
import com.lnsoft.erp.servicebridge.protocol.BridgeRequestMessage;
import com.lnsoft.erp.servicebridge.protocol.BridgeWaitStatus;
import com.lnsoft.erp.servicebridge.protocol.BridgeTopicProperties;
import com.lnsoft.erp.servicebridge.protocol.ServiceBridgeProtocol;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

public class DefaultServiceBridgeClient implements ServiceBridgeClient {

    private final ServiceBridgeClientProperties properties;
    private final BridgeRequestIdGenerator idGenerator;
    private final BridgeRequestStore requestStore;
    private final TaskWaitRegistry waitRegistry;
    private final BridgeCompletionCoordinator completionCoordinator;
    private final BridgeResponseFactory responseFactory;
    private final MessageProducer messageProducer;
    private final BridgeTopicProperties topics;
    private final TransactionTemplate transactionTemplate;

    public DefaultServiceBridgeClient(ServiceBridgeClientProperties properties,
                                      BridgeRequestIdGenerator idGenerator,
                                      BridgeRequestStore requestStore,
                                      TaskWaitRegistry waitRegistry,
                                      BridgeCompletionCoordinator completionCoordinator,
                                      BridgeResponseFactory responseFactory,
                                      MessageProducer messageProducer,
                                      BridgeTopicProperties topics,
                                      TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.requestStore = requestStore;
        this.waitRegistry = waitRegistry;
        this.completionCoordinator = completionCoordinator;
        this.responseFactory = responseFactory;
        this.messageProducer = messageProducer;
        this.topics = topics;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public DeferredResult<ResponseEntity<BridgeHttpResponse>> request(
            String businessType, JsonNode payload) {
        return request(businessType, payload, null);
    }

    @Override
    public DeferredResult<ResponseEntity<BridgeHttpResponse>> request(
            String businessType, JsonNode payload, String traceId) {
        validate(businessType, payload);
        String requestId = idGenerator.nextId();
        DeferredResult<ResponseEntity<BridgeHttpResponse>> deferred =
                new DeferredResult<ResponseEntity<BridgeHttpResponse>>(properties.getTimeout().toMillis());

        final WaitContext context = new WaitContext(requestId, deferred);
        configureCallbacks(context);
        try {
            waitRegistry.register(context);
        } catch (TooManyWaitingRequestsException e) {
            context.tryComplete(responseFactory.unavailable(requestId, e.getMessage()));
            return deferred;
        }

        final LocalDateTime createdAt = LocalDateTime.now();
        final LocalDateTime deadlineAt = createdAt.plusNanos(properties.getTimeout().toNanos());
        final BridgeRequestRecord record = newRecord(
                requestId, businessType, payload, createdAt, deadlineAt);
        final BridgeRequestMessage message = newMessage(record, traceId);

        try {
            transactionTemplate.execute(status -> {
                requestStore.create(record);
                messageProducer.send(
                        topics.getRequestTopic(),
                        properties.getMessageSource(),
                        message);
                return null;
            });
        } catch (RuntimeException e) {
            waitRegistry.remove(requestId, context);
            context.tryComplete(responseFactory.unavailable(requestId, "Failed to submit ServiceBridge request"));
            return deferred;
        }

        completionCoordinator.onCompletionNotification(requestId);
        return deferred;
    }

    @Override
    public Optional<BridgeStoredResult> query(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return requestStore.find(requestId);
    }

    private void configureCallbacks(final WaitContext context) {
        context.getDeferredResult().onTimeout(() -> completionCoordinator.onTimeout(context));
        context.getDeferredResult().onError(error -> {
            context.cancel();
            waitRegistry.remove(context.getRequestId(), context);
        });
        context.getDeferredResult().onCompletion(
                () -> waitRegistry.remove(context.getRequestId(), context));
    }

    private BridgeRequestRecord newRecord(String requestId,
                                          String businessType,
                                          JsonNode payload,
                                          LocalDateTime createdAt,
                                          LocalDateTime deadlineAt) {
        BridgeRequestRecord record = new BridgeRequestRecord();
        record.setRequestId(requestId);
        record.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        record.setBusinessType(businessType);
        record.setRequestPayload(payload);
        record.setWaitStatus(BridgeWaitStatus.WAITING);
        record.setProcessStatus(BridgeProcessStatus.PENDING);
        record.setCreatedAt(createdAt);
        record.setClientDeadlineAt(deadlineAt);
        return record;
    }

    private BridgeRequestMessage newMessage(BridgeRequestRecord record, String traceId) {
        BridgeRequestMessage message = new BridgeRequestMessage();
        message.setProtocolVersion(record.getProtocolVersion());
        message.setMessageId(idGenerator.nextId());
        message.setRequestId(record.getRequestId());
        message.setBusinessType(record.getBusinessType());
        message.setPayload(record.getRequestPayload());
        message.setCreatedAt(record.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        message.setClientDeadlineAt(
                record.getClientDeadlineAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        message.setTraceId(traceId);
        return message;
    }

    private void validate(String businessType, JsonNode payload) {
        if (businessType == null || businessType.trim().isEmpty()) {
            throw new IllegalArgumentException("businessType must not be blank");
        }
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (properties.getTimeout() == null || properties.getTimeout().isZero()
                || properties.getTimeout().isNegative()) {
            throw new IllegalStateException("service.bridge.client.timeout must be greater than zero");
        }
    }
}
