package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.client.model.BridgeHttpResponse;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.protocol.BridgeProcessStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class BridgeResponseFactory {

    private final ObjectMapper objectMapper;

    public BridgeResponseFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<BridgeHttpResponse> fromStored(BridgeStoredResult stored) {
        if (stored.getProcessStatus() == BridgeProcessStatus.SUCCESS) {
            return ResponseEntity.ok(BridgeHttpResponse.of(
                    stored.getRequestId(), "SUCCESS", null, parse(stored.getResultPayload())));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(BridgeHttpResponse.of(
                stored.getRequestId(),
                stored.getErrorCode() == null ? "FAILED" : stored.getErrorCode(),
                stored.getErrorMessage(),
                null));
    }

    public ResponseEntity<BridgeHttpResponse> timeout(String requestId) {
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(
                BridgeHttpResponse.of(requestId, "TIMEOUT", "No result was available before the client timeout", null));
    }

    public ResponseEntity<BridgeHttpResponse> unavailable(String requestId, String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                BridgeHttpResponse.of(requestId, "SERVICE_UNAVAILABLE", message, null));
    }

    private JsonNode parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }
}
