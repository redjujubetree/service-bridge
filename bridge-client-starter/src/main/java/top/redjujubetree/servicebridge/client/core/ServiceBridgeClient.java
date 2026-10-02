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

    Optional<BridgeStoredResult> query(String requestId);
}
