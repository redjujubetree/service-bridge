package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.JsonNode;
import top.redjujubetree.servicebridge.client.config.ServiceBridgeClientProperties;
import top.redjujubetree.servicebridge.client.exception.TooManyWaitingRequestsException;
import top.redjujubetree.servicebridge.client.model.BridgeHttpResponse;
import top.redjujubetree.servicebridge.client.model.BridgeRequestRecord;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;
import top.redjujubetree.servicebridge.protocol.status.BridgeWaitStatus;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import top.redjujubetree.servicebridge.protocol.ServiceBridgeProtocol;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.Optional;

public class DefaultServiceBridgeClient implements ServiceBridgeClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultServiceBridgeClient.class);

    private final ServiceBridgeClientProperties properties;
    private final BridgeRequestIdGenerator idGenerator;
    private final BridgeRequestStore requestStore;
    private final TaskWaitRegistry waitRegistry;
    private final BridgeCompletionCoordinator completionCoordinator;
    private final DefaultBridgeResponseHandler defaultResponseHandler;
    private final BridgeMessagePublisher publisher;
    private final BridgeTopicProperties topics;
    private final TransactionTemplate transactionTemplate;

    public DefaultServiceBridgeClient(ServiceBridgeClientProperties properties,
                                      BridgeRequestIdGenerator idGenerator,
                                      BridgeRequestStore requestStore,
                                      TaskWaitRegistry waitRegistry,
                                      BridgeCompletionCoordinator completionCoordinator,
                                      DefaultBridgeResponseHandler defaultResponseHandler,
                                      BridgeMessagePublisher publisher,
                                      BridgeTopicProperties topics,
                                      TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.requestStore = requestStore;
        this.waitRegistry = waitRegistry;
        this.completionCoordinator = completionCoordinator;
        this.defaultResponseHandler = defaultResponseHandler;
        this.publisher = publisher;
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
        return request(businessType, payload, traceId, defaultResponseHandler);
    }

    @Override
    public <T> DeferredResult<ResponseEntity<T>> request(
            String businessType, JsonNode payload, String traceId,
            BridgeResponseHandler<T> responseHandler) {
        if (responseHandler == null) {
            throw new IllegalArgumentException("responseHandler must not be null");
        }
        validate(businessType, payload, traceId);
        final String requestTopic = topics.requestTopicFor(businessType);
        String requestId = idGenerator.nextId();
        DeferredResult<ResponseEntity<T>> deferred =
                new DeferredResult<ResponseEntity<T>>(properties.getTimeout().toMillis());

        final WaitContext<T> context = new WaitContext<T>(requestId, deferred, responseHandler);
        configureCallbacks(context);
        try {
            waitRegistry.register(context);
        } catch (TooManyWaitingRequestsException e) {
            completeUnavailable(context, e.getMessage());
            return deferred;
        }

        final LocalDateTime createdAt = LocalDateTime.now();
        final LocalDateTime deadlineAt = createdAt.plusNanos(properties.getTimeout().toNanos());
        final BridgeRequestRecord record = newRecord(
                requestId, businessType, payload, traceId, createdAt, deadlineAt);

        try {
            transactionTemplate.execute(status -> {
                requestStore.create(record);
                return null;
            });
        } catch (RuntimeException e) {
            waitRegistry.remove(requestId, context);
            completeUnavailable(context, "Failed to submit ServiceBridge request");
            return deferred;
        }

        try {
            publisher.send(requestTopic, requestId, record.getRequestId());
        } catch (RuntimeException e) {
            log.warn("Failed to send ServiceBridge request {}", requestId, e);
            try {
                Boolean marked = transactionTemplate.execute(status -> {
                    return requestStore.markSubmissionFailed(
                            requestId, "Failed to send REQUEST message", LocalDateTime.now());
                });
                if (!Boolean.TRUE.equals(marked)) {
                    log.warn("MQ send failed but request {} had already changed state", requestId);
                }
            } catch (RuntimeException updateFailure) {
                log.error("Failed to mark request {} as MQ_SEND_FAILED", requestId, updateFailure);
            }
            waitRegistry.remove(requestId, context);
            completeUnavailable(context, "Failed to send ServiceBridge request");
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

    private <T> void configureCallbacks(final WaitContext<T> context) {
        context.getDeferredResult().onTimeout(() -> completionCoordinator.onTimeout(context));
        context.getDeferredResult().onError(error -> {
            context.cancel();
            waitRegistry.remove(context.getRequestId(), context);
        });
        context.getDeferredResult().onCompletion(
                () -> waitRegistry.remove(context.getRequestId(), context));
    }

    private <T> void completeUnavailable(WaitContext<T> context, String message) {
        if (!context.tryClaim()) {
            return;
        }
        try {
            context.finish(context.getResponseHandler().unavailable(context.getRequestId(), message));
        } catch (RuntimeException handlerFailure) {
            context.finishError(handlerFailure);
        }
    }

    private BridgeRequestRecord newRecord(String requestId,
                                          String businessType,
                                          JsonNode payload,
                                          String traceId,
                                          LocalDateTime createdAt,
                                          LocalDateTime deadlineAt) {
        BridgeRequestRecord record = new BridgeRequestRecord();
        record.setRequestId(requestId);
        record.setProtocolVersion(ServiceBridgeProtocol.VERSION);
        record.setBusinessType(businessType);
        record.setRequestPayload(payload.toString());
        record.setTraceId(traceId);
        record.setWaitStatus(BridgeWaitStatus.WAITING);
        record.setProcessStatus(BridgeProcessStatus.PENDING);
        record.setCreatedAt(createdAt);
        record.setClientDeadlineAt(deadlineAt);
        return record;
    }

    private void validate(String businessType, JsonNode payload, String traceId) {
        if (businessType == null || businessType.trim().isEmpty()) {
            throw new IllegalArgumentException("businessType must not be blank");
        }
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (traceId != null && traceId.length() > 127) {
            throw new IllegalArgumentException("traceId must not exceed 127 characters");
        }
        if (properties.getTimeout() == null || properties.getTimeout().isZero()
                || properties.getTimeout().isNegative()) {
            throw new IllegalStateException("service.bridge.client.timeout must be greater than zero");
        }
    }
}
