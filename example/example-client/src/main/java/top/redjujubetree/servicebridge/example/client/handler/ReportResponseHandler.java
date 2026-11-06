package top.redjujubetree.servicebridge.example.client.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import top.redjujubetree.servicebridge.client.core.BridgeResponseHandler;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;

@Component
public class ReportResponseHandler implements BridgeResponseHandler<JsonNode> {

    private final ObjectMapper objectMapper;

    public ReportResponseHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<JsonNode> fromStored(BridgeStoredResult stored) {
        if (stored.getProcessStatus() == BridgeProcessStatus.SUCCESS) {
            try {
                return ResponseEntity.ok(objectMapper.readTree(stored.getResultPayload()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Invalid stored report result", e);
            }
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                error(stored.getRequestId(),
                        stored.getErrorCode() == null ? "FAILED" : stored.getErrorCode(),
                        stored.getErrorMessage()));
    }

    @Override
    public ResponseEntity<JsonNode> timeout(String requestId) {
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(
                error(requestId, "TIMEOUT", "No result was available before the client timeout"));
    }

    @Override
    public ResponseEntity<JsonNode> unavailable(String requestId, String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                error(requestId, "SERVICE_UNAVAILABLE", message));
    }

    private ObjectNode error(String requestId, String code, String message) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("requestId", requestId);
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
