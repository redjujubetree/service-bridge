package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.JsonNode;
import top.redjujubetree.servicebridge.client.model.BridgeHttpResponse;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Optional;

public interface ServiceBridgeClient {

    DeferredResult<ResponseEntity<BridgeHttpResponse>> request(String businessType, JsonNode payload);

    DeferredResult<ResponseEntity<BridgeHttpResponse>> request(
            String businessType, JsonNode payload, String traceId);

    /**
     * Submits a request with an application-defined HTTP response for every completion path.
     * Existing custom implementations may override this method to support custom responses.
     */
    default <T> DeferredResult<ResponseEntity<T>> request(
            String businessType, JsonNode payload, String traceId, BridgeResponseHandler<T> responseHandler) {
        throw new UnsupportedOperationException("Custom responses are not supported by this ServiceBridgeClient");
    }

    Optional<BridgeStoredResult> query(String requestId);
}
